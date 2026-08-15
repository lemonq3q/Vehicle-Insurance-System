import { ElMessage } from 'element-plus';

/**
 * 封装 Element Plus 全局消息入口，统一车险页面对普通、成功、警告和错误结果的反馈方式。
 * 页面只传入业务文案，不直接依赖具体 UI 组件配置，便于后续统一调整展示策略。
 */
class Message {

  static info(msg) {
    ElMessage(msg);
  }

  static primary(msg) {
    ElMessage.primary(msg);
  }

  static success(msg) {
    ElMessage({
      message: msg,
      type: 'success',
    });
  }

  static warning(msg) {
    ElMessage({
      message: msg,
      type: 'warning',
    });
  }

  static error(msg) {
    ElMessage.error(msg);
  }
  
}

export default Message;
