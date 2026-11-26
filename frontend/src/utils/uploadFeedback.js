import Message from './message';

/**
 * 处理上传组件数量超限回调，向用户说明当前资料上限与替换方法。
 * 名称和数量来自上传区域既有配置；仅作本地警告，不删除已有文件、不发起上传。
 * 一次选择超限和已有文件后继续添加均复用此入口，消息参与统一 warning 去重。
 * @param {string} name 上传区域的业务名称。
 * @param {number} limit 当前区域允许的文件总数。
 */
export function warnUploadLimit(name, limit) {
  Message.warning(limit === 1
    ? name + '最多只能上传1个文件，如需更换，请先移除已上传的文件'
    : name + '最多只能上传' + limit + '个文件，请减少本次选择数量或先移除已上传的文件');
}
