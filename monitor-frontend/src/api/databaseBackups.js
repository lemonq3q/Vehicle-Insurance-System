import request from "./request";
import { databaseBackupMock } from "@/mock/databaseBackups";
/** 备份目录和单文件下载沿用监控鉴权与统一失败通知，不创建后台导出任务。 */
export const databaseBackupApi = {
  files: (params) =>
    process.env.VUE_APP_BACKUP_MOCK === "true"
      ? databaseBackupMock.files(params)
      : request.get("/database-backups/files", { params }),
  download: (key) =>
    process.env.VUE_APP_BACKUP_MOCK === "true"
      ? databaseBackupMock.download(key)
      : request.get("/database-backups/download", { params: { key } }),
};
