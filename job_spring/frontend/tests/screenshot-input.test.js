import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hasDraggedFiles, readDroppedScreenshot, readPastedScreenshot, selectSingleScreenshot } from '../src/screenshot-input.js'

const image = (type = 'image/png', size = 128, name = 'virtual.png') => ({ type, size, name })
const item = file => ({ kind: 'file', type: file.type, getAsFile: () => file })
const unreadableText = { kind: 'string', type: 'text/plain', getAsString() { throw new Error('Text must not be read') } }

test('all entrances keep exactly one file without rewriting its content or metadata', () => {
  const file = image()
  assert.equal(selectSingleScreenshot([file]).file, file)
  assert.equal(readDroppedScreenshot({ files: [file] }).file, file)
  assert.equal(readPastedScreenshot({ items: [item(file)] }).file, file)
})

test('selection accepts the exact 5 MiB boundary and refuses zero or one extra byte', () => {
  assert.equal(selectSingleScreenshot([image('image/png', 5 * 1024 * 1024)]).error, undefined)
  assert.match(selectSingleScreenshot([image('image/png', 0)]).error, /非空/)
  assert.match(selectSingleScreenshot([image('image/png', 5 * 1024 * 1024 + 1)]).error, /5 MiB/)
})

test('multiple files are refused instead of silently taking the first image', () => {
  const files = [image(), image('image/jpeg')]
  assert.match(selectSingleScreenshot(files).error, /一张/)
  assert.match(readDroppedScreenshot({ files }).error, /一张/)
  assert.match(readPastedScreenshot({ items: files.map(item) }).error, /一张/)
})

test('drag hover checks types without trying to access protected file contents', () => {
  const transfer = { types: ['Files'], items: [{ kind: 'file', getAsFile() { throw new Error('Must not read on hover') } }] }
  assert.equal(hasDraggedFiles(transfer), true)
  assert.equal(hasDraggedFiles({ items: [{ kind: 'file' }] }), true)
  assert.equal(hasDraggedFiles({ types: ['text/plain', 'text/uri-list'] }), false)
  assert.equal(hasDraggedFiles(null), false)
})

test('folders are refused before reading file data or traversing directory contents', () => {
  const directory = { kind: 'file', webkitGetAsEntry: () => ({ isDirectory: true }), getAsFile() { throw new Error('Must not read folder') } }
  assert.match(readDroppedScreenshot({ items: [directory], files: [image()] }).error, /文件夹/)
})

test('drop supports item-only transfers and reports unavailable data', () => {
  const file = image()
  assert.equal(readDroppedScreenshot({ items: [item(file)] }).file, file)
  assert.match(readDroppedScreenshot({ items: [{ kind: 'file', getAsFile: () => null }] }).error, /无法读取/)
})

test('a URL or text drop is not fetched or converted into an image', () => {
  assert.match(readDroppedScreenshot({ types: ['text/uri-list'], items: [unreadableText],
    getData() { throw new Error('Must not read URLs') } }).error, /本地图片/)
  assert.match(readDroppedScreenshot(null).error, /本地图片/)
})

test('text and HTML paste remain unhandled without reading their content', () => {
  assert.deepEqual(readPastedScreenshot({ items: [unreadableText, { kind: 'string', type: 'text/html' }],
    getData() { throw new Error('Must not read text') } }), { handled: false })
  assert.deepEqual(readPastedScreenshot(null), { handled: false })
})

test('an image with accompanying text is consumed once and its text is ignored', () => {
  const file = image('image/jpeg')
  assert.deepEqual(readPastedScreenshot({ items: [unreadableText, item(file)] }), { handled: true, file })
})

test('paste supports browsers exposing only files, but ignores non-image files', () => {
  const file = image()
  assert.deepEqual(readPastedScreenshot({ files: [file] }), { handled: true, file })
  assert.deepEqual(readPastedScreenshot({ files: [image('application/pdf')] }), { handled: false })
})

test('unavailable clipboard image data is handled with a specific error', () => {
  const result = readPastedScreenshot({ items: [{ kind: 'file', type: 'image/png', getAsFile: () => null }] })
  assert.equal(result.handled, true)
  assert.match(result.error, /无法读取剪贴板图片/)
})

test('empty or oversized clipboard images are rejected through the common rule', () => {
  assert.match(readPastedScreenshot({ items: [item(image('image/png', 0))] }).error, /非空/)
  assert.match(readPastedScreenshot({ files: [image('image/png', 5 * 1024 * 1024 + 1)] }).error, /5 MiB/)
})

test('HEIC and HEIF declarations give conversion guidance through every entrance', () => {
  for (const type of ['image/heic', 'image/heif', 'image/heic-sequence', 'IMAGE/HEIF-SEQUENCE']) {
    const file = image(type, 1024, 'photo.jpg')
    for (const result of [selectSingleScreenshot([file]), readDroppedScreenshot({ files: [file] }), readPastedScreenshot({ items: [item(file)] })]) {
      assert.match(result.error, /HEIC\/HEIF/)
      assert.match(result.error, /导出为JPEG或PNG/)
      assert.match(result.error, /后缀无效/)
      assert.equal(result.file, undefined)
    }
  }
})

test('HEIF extension is a hint when the system omits a specific media type', () => {
  for (const type of ['', 'application/octet-stream']) {
    for (const name of ['photo.HEIC', 'photo.heif']) {
      assert.match(selectSingleScreenshot([image(type, 1024, name)]).error, /HEIC\/HEIF/)
    }
  }
})

test('system-converted JPEG and PNG can retain an old HEIC filename', () => {
  for (const type of ['image/jpeg', 'image/png', 'IMAGE/JPEG']) {
    const file = image(type, 1024, 'photo.HEIC')
    assert.equal(selectSingleScreenshot([file]).file, file)
  }
})
