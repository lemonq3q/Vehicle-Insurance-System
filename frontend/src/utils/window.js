/**
 * 按 1920 设计稿宽度动态设置根元素字号，使使用 rem 的车险页面等比例缩放。
 * 计算宽度限制在 1024 至 4096 之间，避免过窄或超宽屏幕导致控件尺寸失控。
 */
function autoSize() {
  var psdWidth = 1920;
  var preFontSize = 96;
  var curScreenWidth = document.documentElement.clientWidth;
  if(curScreenWidth > 4096) {
    curScreenWidth = 4096;
  }
  else if(curScreenWidth < 1024){
    curScreenWidth = 1024;
  }
  var curFontSize = (curScreenWidth*preFontSize) / psdWidth;
  document.documentElement.style.fontSize = curFontSize + "px";
}

export default autoSize;
