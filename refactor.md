# 重构[下载器项目](https://github.com/SirLYC/YC-Downloader)

原仓库(downloader组件库)基于java编写, 这一次的重构版本采用kotlin, 在重构项目的同时复习kotlin语法

## 入门

#### 1. 用户下载操作
- 新建任务
- 删除任务
- 取消任务
- 恢复下载
- 暂停下载

#### 2. YCDownloader提供API
- submit - 新建任务并提交
- delete - 删除任务
- cancel - 取消任务
- pause - 暂停下载
- startOrResume - 恢复下载
````java
class YCDownloader {
    // submit a task to downloader service
    void submit(String url, String path, String filename, SubmitListener listener);

    // delete this download task from db
    void delete(long id, boolean deleteFile);

    // cancel a task and delete it from {@link DownloadManager}
    void cancel(long id);

    //  pause the task is it's running
    void pause(long id);

    // start a task or resume a task
    void startOrResume(long id, boolean restart);
}
````