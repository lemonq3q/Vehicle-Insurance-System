import { inject, onActivated } from 'vue';

/**
 * 工作台内的工单列表导航上下文，由HomePage提供并随其卸载释放。
 * 只维护本次登录工作台的内存缓存、修改版本与滚动位置，不将客户数据持久化到浏览器存储。
 */
export const WORKORDER_NAVIGATION_KEY = Symbol('workorderNavigation');

/**
 * 允许作为工单详情来源的四个列表及其组件名；缓存以路径区分，避免不同业务入口互相覆盖。
 * 详情和编辑表单不参与缓存，退出工作台时所有缓存随父组件一起销毁。
 */
export const WORKORDER_LIST_COMPONENTS = {
  '/home/allWorkorder': 'AllWorkOrder',
  '/home/acceptWorkOrder': 'AcceptWorkOrder',
  '/home/dispatchWorkOrder': 'DispatchWorkOrder',
  '/home/renewWorkorder': 'RenewWorkorder'
};

/**
 * 校验详情传入的列表来源，仅允许本站已知工单列表，保留该来源原有query但不接受外部或其他业务地址。
 * @param {*} value returnTo路由参数，数组、空值及非法路径统一回退到工单管理。
 * @returns {string} 安全的列表返回地址。
 */
export function workorderReturnTarget(value) {
  if (typeof value !== 'string') return '/home/allWorkorder';
  const path = value.split(/[?#]/)[0];
  return Object.prototype.hasOwnProperty.call(WORKORDER_LIST_COMPONENTS, path) ? value : '/home/allWorkorder';
}

/**
 * 返回进入详情前的工单列表。历史上一页正是该来源时回退以保持正常浏览器前进/后退语义；
 * 直接打开详情或历史不匹配时替换当前页，避免跳出系统或形成列表与详情之间的返回循环。
 * @param {object} router 当前Vue Router实例。
 * @param {*} source 详情returnTo参数。
 * @returns {*} 路由操作的返回值，不修改列表条件或业务数据。
 */
export function returnToWorkorderList(router, source) {
  const target = workorderReturnTarget(source);
  if (router.options.history.state.back === target) return router.back();
  return router.replace(target);
}

/**
 * 记录main及表格内部滚动容器的位置；列表组件由KeepAlive保留，其余页面不存储客户数据副本。
 * @param {HTMLElement|null} main 工作台实际滚动视窗。
 * @returns {object|null} 视窗纵横位置及各表格滚动位置，视窗尚未挂载时为空。
 */
export function captureWorkorderScroll(main) {
  if (!main) return null;
  return {
    top: main.scrollTop, left: main.scrollLeft,
    tables: Array.from(main.querySelectorAll('.el-table__body-wrapper .el-scrollbar__wrap'),
      table => ({ top: table.scrollTop, left: table.scrollLeft }))
  };
}

/**
 * 在缓存列表重新挂载或数据刷新后恢复main和表格纵横滚动；无快照时回到内容顶部。
 * @param {HTMLElement|null} main 当前工作台视窗。
 * @param {object|null} snapshot 当前列表离开时的滚动快照。
 * @returns {void} 仅更新滚动位置，不触发查询或修改筛选条件。
 */
export function restoreWorkorderScroll(main, snapshot) {
  if (!main) return;
  main.scrollTop = snapshot?.top || 0;
  main.scrollLeft = snapshot?.left || 0;
  main.querySelectorAll('.el-table__body-wrapper .el-scrollbar__wrap').forEach((table, index) => {
    table.scrollTop = snapshot?.tables[index]?.top || 0;
    table.scrollLeft = snapshot?.tables[index]?.left || 0;
  });
}

/**
 * 缓存列表首次挂载沿用原onMounted查询；单纯查看详情返回时不重复请求，不重置任何页面状态。
 * 仅在详情已成功保存后按现有筛选/页码刷新数据，随后重新恢复滚动，防止缓存展示旧工单状态。
 * @param {Function} refresh 列表现有异步查询函数，不另建接口或绕过请求封装。
 * @returns {void} 注册激活钩子；无工作台上下文时保持列表原有行为。
 */
export function useWorkorderListRefresh(refresh) {
  const navigation = inject(WORKORDER_NAVIGATION_KEY, null);
  let revision = navigation?.revision.value;
  onActivated(async () => {
    if (!navigation || revision === navigation.revision.value) return;
    const pendingRevision = navigation.revision.value;
    const path = navigation.currentPath();
    try {
      await refresh();
      revision = pendingRevision;
    } finally {
      await navigation.restoreScroll(path);
    }
  });
}
