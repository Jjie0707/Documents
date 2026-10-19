export const jobStatuses = {
  TO_APPLY: { label: '待投递', color: '#899254' },
  APPLIED: { label: '已投递', color: '#5289a8' },
  WRITTEN_TEST: { label: '笔试中', color: '#9c7838' },
  INTERVIEWING: { label: '面试中', color: '#866bb0' },
  OFFER: { label: '已获 Offer', color: '#387c54' },
  REJECTED: { label: '未通过', color: '#b56054' },
  WITHDRAWN: { label: '已放弃', color: '#7c8386' },
}
export function jobStatus(value) {
  return jobStatuses[value] || { label: '未知状态', color: '#7c8386' }
}
