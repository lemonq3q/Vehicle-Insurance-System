import { ElMessage } from 'element-plus';

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