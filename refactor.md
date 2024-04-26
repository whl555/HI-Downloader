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
    // download API的具体实现类
    private static BaseServiceManager serviceManager;
    // submit a task to downloader service
    static void submit(String url, String path, String filename, SubmitListener listener);

    // delete this download task from db
    static void delete(long id, boolean deleteFile);

    // cancel a task and delete it from {@link DownloadManager}
    static void cancel(long id);

    //  pause the task is it's running
    static void pause(long id);

    // start a task or resume a task
    static void startOrResume(long id, boolean restart);
}
````

#### 3. download API的包装链
YCDownloader -> BaseServiceManager -> IDownloadService(IDownloadService$StubImp) -> DownloadManager
````java
// YCDownloader --> BaseServiceManager
class YCDownloader {
    // download API的具体实现类
    private static BaseServiceManager serviceManager;
}

// BaseServiceManager --> IDownloadService(IDownloadService$StubImp)
abstract class BaseServiceManager {
    IDownloadService downloadService;
    
    xxx api() {
        DownloadExecutors.command.execute(() -> {
            try {
                waitingForConnection();
                downloadService.api(id);
            } catch (RemoteException e) {
                Logger.e("DownloadController", "cannot startOrResume", e);
            }
        });
    }
}

// IDownloadService(IDownloadService$StubImp) --> DownloadManager
public final class IDownloadService$StubImp extends IDownloadService.Stub {
    private final DownloadManager downloadManager = DownloadManager.instance();
}
````

RemoteServiceManager & LocalServiceManager区别
```java
abstract class BaseServiceManager {
    IDownloadService downloadService;
    
    abstract void connectToService();
}
class RemoteServiceManager extends BaseServiceManager {
    abstract void connectToService() {
        // aidl RemoteDownloadService -> IDownloadService
        downloadService = IDownloadService.Stub.asInterface(service);
    }
}

class LocalServiceManager extends BaseServiceManager {
    abstract void connectToService() {
        // LocalDownloadService -> IDownloadService
        downloadService = LocalDownloadService.asInterface(service);
    }
}
```

#### 4. downloadAPI的具体实现类 -- DownloadManager

a. cancel
- taskTable
- class DownloadTask
```java
class DownloadManager {
    // key - insertId
    private final LongSparseArray<DownloadTask> taskTable = new LongSparseArray<>();
    
    public void cancel(long id) {
        DownloadExecutors.message.execute(() -> {
            DownloadTask downloadTask = taskTable.get(id);
            if (downloadTask == null) return;
            downloadTask.cancel();
            notifyDownloadInfoRemoved(id);
        });
    }
}
```

b. submit
- class DownloadInfo
- infoTables
- client class OkhHttpClient 
- waitingTasksId 所有等待的task的insertId列表
- runningTasksId
- errorTasksId
- pausingTasksId
```java
class DownloadManager {
    // key - insertId
    private final LongSparseArray<DownloadInfo> infoTable = new LongSparseArray<>();
    private final OkHttpClient client;
    
    @WorkerThread
    private void submitInner(String url, String path, String filename, ISubmitCallback listener) {
        DownloadInfo downloadInfo = new DownloadInfo(null, url, path,
                filename, true, WAITING,
                0, 0, null, new Date(), null, null);
        try {
            Long insertId = PersistUtil.persistDownloadInfo(daoSession, downloadInfo, null);
            DownloadExecutors.message.execute(() -> {
                if (insertId != null) {
                    infoTable.put(insertId, downloadInfo);
                    DownloadTask downloadTask = new DownloadTask(downloadInfo, client);
                    taskTable.put(insertId, downloadTask);
                    waitingTasksId.add(insertId);
                    try {
                        listener.submitSuccess(downloadInfo);
                    } catch (RemoteException e) {
                        Logger.e(TAG, "submitSuccess", e);
                    }
                    notifyDownloadManagerArrive(downloadInfo);
                    schedule();
                } else {
                    try {
                        listener.submitFail("创建任务失败");
                    } catch (RemoteException e) {
                        Logger.e(TAG, "submitFail", e);
                    }
                }
            });
        } catch (Exception e) {
            try {
                listener.submitFail(e.getLocalizedMessage());
            } catch (RemoteException e1) {
                Logger.e(TAG, "submitFail", e1);
            }
        }
    }
}
```

c. startOrResume
略

d. DownloadManager中重要的成员变量
```
1. taskTable、infoTable
taskTable: 所有的DownloadTask, insertId作为key值
infoTable: 所有的DownloadInfo, insertId作为key值
DownloadTask: 对于下载任务的抽象(不存入数据库)
DownloadInfo: 一个数据库实体类, 下载任务的信息(存入数据库)

2. runningTasksId、waitingTasksId、pausingTasksId、errorTasksId
runningTasksId: 下载中状态的DownloadTask
waitingTasksId: 就绪状态的DownloadTask
pausingTasksId: 中止状态的DownloadTask
errorTasksId: 下载错误的DownloadTask

3. downloadCallback、downloadTasksChangeCallback等
下载过程相关的回调
```

e. 从重要属性的角度解析5个下载API
- submit
1. 提交的url、path、filename新建DownloadInfo, 获取数据库表单id(以后简称id)后, 插入infoTable
2. 以新建的DownloadInfo新建DownloadTask, 插入到taskTable
3. 将id插入到waitingTasksId
4. 提交成功回调、onNewDownloadTaskArrive回调
5. 调度DownloadTask, 将id从waitingTasksId添加到runningTasksId
- startOrResume
通过scheduleAfterEnqueue, 调度DownloadTask, 将id从waitingTasksId添加到runningTasksId

以下三个API与DownloadTask类相关
- cancel
- pause
- delete


#### 5. 下载任务核心类 -- DownloadTask

a. DownloadTask中重要的成员变量
- PENDING、CONNECTING、RUNNING、STOPPING、PAUSED、FINISH、WAITING、CANCELED、ERROR、FATAL_ERROR等10个状态; state; 
此处可以仿效线程、进程画出状态流转图
- fileLock、stateLock、runLock、semaphore、startDownloadLatch等多线程并发相关变量
fileLock: 文件读写加的锁 ?
stateLock: 更新state需要加的锁 ?
runLock: ?
semaphore: ?
startDownloadLatch: ?


b. start
上锁 -> state转变 -> 执行execute方法
```java
class DownloadTarget {
    boolean start() {
        try {
            // state上锁
            stateLock.lock();
            // 抢运行锁
            if (runLock.tryLock()) {
                try {
                    if (restart) {
                        // resume
                        if (state != RUNNING && state != STOPPING && state != CANCELED) {
                            this.state = PENDING;
                            stateChange();
                            DownloadExecutors.io.execute(() -> {
                                try {
                                    runLock.lock();
                                    this.execute();
                                } finally {
                                    runLock.unlock();
                                }
                            });
                            return true;
                        }
                    } else if (state != RUNNING && state != STOPPING && state != CANCELED && state != FINISH) {
                        // start
                        state = PENDING;
                        stateChange();
                        DownloadExecutors.io.execute(() -> {
                            try {
                                runLock.lock();
                                this.execute();
                            } finally {
                                runLock.unlock();
                            }
                        });
                        return true;
                    }

                } finally {
                    runLock.unlock();
                }
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }
}
```

c. pause
上锁 -> state改变
```java
class DownloadTask {
    void pause() {
        try {
            stateLock.lock();
            if (runLock.tryLock()) {
                try {
                    if (state == PAUSED || state == CANCELED) {
                        return;
                    }
                    state = PAUSED;
                    stateChange();
                    downloadManager.onDownloadPaused(downloadInfo.getId());
                } finally {
                    runLock.unlock();
                }
            } else {
                if (state != RUNNING && state != CONNECTING) {
                    return;
                }
                state = STOPPING;
                targetState = PAUSED;
                stateChange();
                interruptBlocking();
                downloadManager.onDownloadStopping(downloadInfo.getId());
            }
        } finally {
            stateLock.unlock();
        }
    }
}
```

