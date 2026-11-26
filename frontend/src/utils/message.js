import { acquireMessage } from './messageLock';
import { ElMessage } from 'element-plus';

/**
 * 封装 Element Plus 全局消息入口，统一车险页面对普通、成功、警告和错误结果的反馈方式。
 * 页面只传入业务文案，不直接依赖具体 UI 组件配置，便于后续统一调整展示策略。
 */
class Message {
  /**
   * 统一接收提示类型和文案，重复消息直接跳过；销毁或创建异常释放占用。
   * 返回组件实例供调用方关闭，两秒有效期在获取锁时判断，不改变错误分级。
   */
  static show(type, message) {
    const release = acquireMessage(type, message);
    if (!release) return;
    try {
      return ElMessage({ type, message, onVnodeUnmounted: release });
    } catch (error) { release(); throw error; }
  }


  static info(msg) {
    return Message.show('info', msg);
  }

  static primary(msg) {
    return Message.show('primary', msg);
  }

  static success(msg) {
    return Message.show('success', msg);
  }

  static warning(msg) {
    return Message.show('warning', msg);
  }

  static error(msg) {
    return Message.show('error', msg);
  }
  
}

export default Message;
