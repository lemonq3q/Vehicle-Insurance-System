import { ElLoading } from "element-plus";

class Loading {
  static loadingInstance = null;

  static open() {
    this.loadingInstance = ElLoading.service({
    fullscreen: true,
    background: 'rgba(0, 0, 0, 0.7)',
    text: '处理中...'
  });
  }

  static close() {
    if(this.loadingInstance != null){
      this.loadingInstance.close();
    }
  }
}

export default Loading;