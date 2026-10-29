const MAX_IMAGE_BYTES = 5 * 1024 * 1024

// 三种入口共用单文件与体积规则；实际格式、像素和解码仍由 Java 最终确认。
export function selectSingleScreenshot(files) {
  const selected = Array.from(files || [])
  if (selected.length !== 1) return { error: '一次只能导入一张截图，请重新选择。' }
  if (!selected[0].size) return { error: '请选择非空的PNG或JPEG图片。' }
  if (selected[0].size > MAX_IMAGE_BYTES) return { error: '图片不能超过5 MiB。' }
  return { file: selected[0] }
}

// dragover 阶段文件内容可能处于保护状态，只查看类型，不提前取文件。
export function hasDraggedFiles(transfer) {
  return Array.from(transfer?.types || []).some(type => type.toLowerCase() === 'files')
    || Array.from(transfer?.items || []).some(item => item.kind === 'file')
}

export function readDroppedScreenshot(transfer) {
  const items = Array.from(transfer?.items || []).filter(item => item.kind === 'file')
  if (items.some(item => item.webkitGetAsEntry?.()?.isDirectory)) {
    return { error: '不支持拖入文件夹，请选择一张PNG或JPEG截图。' }
  }
  let files = Array.from(transfer?.files || [])
  if (!files.length && items.length) {
    files = items.map(item => item.getAsFile())
    if (files.some(file => !file)) return { error: '无法读取拖入的文件，请使用“选择截图”。' }
  }
  if (!files.length) return { error: '请拖入本地图片文件，图片链接不能直接作为截图。' }
  return selectSingleScreenshot(files)
}

export function readPastedScreenshot(transfer) {
  const imageItems = Array.from(transfer?.items || [])
    .filter(item => item.kind === 'file' && item.type?.toLowerCase().startsWith('image/'))
  let files
  if (imageItems.length) {
    files = imageItems.map(item => item.getAsFile())
    if (files.some(file => !file)) return { handled: true, error: '无法读取剪贴板图片，请使用“选择截图”。' }
  } else {
    // 部分浏览器只暴露 files；不读取文本、HTML 或图片链接。
    files = Array.from(transfer?.files || []).filter(file => file.type?.toLowerCase().startsWith('image/'))
  }
  if (!files.length) return { handled: false }
  return { handled: true, ...selectSingleScreenshot(files) }
}
