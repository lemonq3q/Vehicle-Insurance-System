import { ElLoading } from "element-plus";

/**
 * 管理全局全屏加载遮罩的唯一实例，供跨页面的保存、导入和识别流程复用。
 * 重复关闭不会报错；调用方应在异步流程的 finally 中关闭，避免异常后遮罩残留。
 */
class Loading {
  static loadingInstance = null;

  /**

   * * 创建覆盖整个页面的处理中遮罩，并记录 Element Plus 返回的实例以便关闭。

   */
  static open() {
    this.loadingInstance = ElLoading.service({
    fullscreen: true,
    background: 'rgba(0, 0, 0, 0.7)',
    text: '处理中...'
  });
  }

  /**

   * * 关闭当前遮罩；尚未打开时不执行任何动作，保证异常清理路径可以安全调用。

   */
  static close() {
    if(this.loadingInstance != null){
      this.loadingInstance.close();
    }
  }
}

export default Loading;
