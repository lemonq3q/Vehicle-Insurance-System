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