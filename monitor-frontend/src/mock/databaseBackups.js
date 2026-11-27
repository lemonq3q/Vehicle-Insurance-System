/**
 * 按统一索引组织目录、所属日期和原文件下载的联调样例，不连接真实OSS。
 * 数据形状与监控接口保持一致，开关仍由databaseBackups API封装控制。
 */
export const databaseBackupMock = {
  async files({ path = "", startDate, endDate }) {
    const day = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Asia/Shanghai",
    }).format(new Date());
    const matches =
      (!startDate || startDate <= day) && (!endDate || endDate >= day);
    const folder = (name, target) => ({
      name,
      path: target,
      backupDate: day,
      dataRangeKnown: true,
      dataStartDate: day,
      dataEndDate: day,
    });
    const branches = {
      "": [folder("full", "full/"), folder("binlog", "binlog/")],
      "full/": [folder(day, `full/${day}/`)],
      [`full/${day}/`]: [
        folder("example-backup", `full/${day}/example-backup/`),
      ],
      "binlog/": [folder("example-server", "binlog/example-server/")],
      "binlog/example-server/": [folder(day, `binlog/example-server/${day}/`)],
    };
    const name =
      path === `full/${day}/example-backup/`
        ? "database.sql.gz"
        : path === `binlog/example-server/${day}/`
        ? "mysql-bin.000001"
        : "";
    return {
      path,
      folders: matches ? branches[path] || [] : [],
      files:
        matches && name
          ? [
              {
                key: `database-backup/prod/${path}${name}`,
                name,
                sizeBytes: 1024,
                backupDate: day,
                dataRangeKnown: true,
                dataStartDate: day,
                dataEndDate: day,
                modifiedAt: new Date().toISOString(),
              },
            ]
          : [],
      nextMarker: "",
    };
  },
  async download(key) {
    return {
      name: key.split("/").pop(),
      downloadUrl:
        "data:application/json;charset=utf-8,%7B%22mock%22%3Atrue%7D",
      expiresAt: new Date(Date.now() + 600000).toISOString(),
    };
  },
};
