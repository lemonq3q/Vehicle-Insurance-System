/** 目录与单文件下载响应契约；mock下载JSON样例，不连接OSS。 */
export const databaseBackupMock = {
  async files({ path = "" }) {
    return {
      path,
      folders: path
        ? []
        : [
            { path: "full/", name: "full" },
            { path: "binlog/", name: "binlog" },
            { path: "catalog/", name: "catalog" },
          ],
      files:
        path === "catalog/"
          ? [
              {
                key: "database-backup/prod/catalog/latest.json",
                name: "latest.json",
                sizeBytes: 240,
                modifiedAt: "2026-11-26T04:00:00Z",
              },
            ]
          : [],
      nextMarker: "",
    };
  },
  async download() {
    return {
      name: "latest.json",
      downloadUrl:
        "data:application/json;charset=utf-8,%7B%22mock%22%3Atrue%7D",
      expiresAt: "2026-11-26T04:10:00Z",
    };
  },
};
