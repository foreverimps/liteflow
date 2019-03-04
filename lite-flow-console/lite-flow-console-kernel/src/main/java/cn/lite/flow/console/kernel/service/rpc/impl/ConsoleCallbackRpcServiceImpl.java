package cn.lite.flow.console.kernel.service.rpc.impl;

import cn.lite.flow.common.utils.DateUtils;
import cn.lite.flow.console.client.service.ConsoleCallbackRpcService;
import cn.lite.flow.console.common.exception.ConsoleRuntimeException;
import cn.lite.flow.console.model.basic.TaskInstance;
import cn.lite.flow.console.model.basic.TaskVersion;
import cn.lite.flow.console.model.consts.TaskVersionFinalStatus;
import cn.lite.flow.console.model.consts.TaskVersionStatus;
import cn.lite.flow.console.service.TaskInstanceService;
import cn.lite.flow.console.service.TaskVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by luya on 2018/11/19.
 */
@Service("consoleCallbackRpcServiceImpl")
public class ConsoleCallbackRpcServiceImpl implements ConsoleCallbackRpcService {

    private final static Logger LOG = LoggerFactory.getLogger(ConsoleCallbackRpcServiceImpl.class);

    @Autowired
    private TaskInstanceService taskInstanceService;

    @Autowired
    private TaskVersionService taskVersionService;


    @Transactional("consoleTxManager")
    @Override
    public void success(long instanceId) throws ConsoleRuntimeException {
        TaskInstance taskInstance = taskInstanceService.getById(instanceId);
        if (taskInstance == null) {
            throw new ConsoleRuntimeException("未查询到相关实例");
        }

        int currentStatus = TaskVersionStatus.RUNNING.getValue();
        int targetStatus = TaskVersionStatus.SUCCESS.getValue();

        if (taskInstance.getStatus() != targetStatus) {
            Date now = DateUtils.getNow();
            TaskInstance updateInstance = new TaskInstance();
            updateInstance.setId(instanceId);
            updateInstance.setStatus(targetStatus);
            updateInstance.setMsg("success");
            updateInstance.setRunEndTime(now);
            if (taskInstanceService.updateWithStatus(updateInstance, currentStatus) == 1) {

                TaskVersion taskVersion = taskVersionService.getById(taskInstance.getTaskVersionId());
                if (taskVersion == null) {
                    throw new ConsoleRuntimeException("未查询到相关任务版本");
                }
                if (taskVersion.getStatus() == targetStatus) {
                    return;
                }

                TaskVersion updateVersion = new TaskVersion();
                updateVersion.setId(taskInstance.getTaskVersionId());
                updateVersion.setStatus(targetStatus);
                updateVersion.setFinalStatus(TaskVersionFinalStatus.SUCCESS.getValue());
                taskVersionService.updateWithStatus(updateVersion, currentStatus);
            } else {
                throw new ConsoleRuntimeException("更新实例状态失败");
            }
        }
    }

    @Transactional("consoleTxManager")
    @Override
    public void running(long instanceId, String msg) throws ConsoleRuntimeException {
        TaskInstance taskInstance = taskInstanceService.getById(instanceId);
        if (taskInstance == null) {
            throw new ConsoleRuntimeException("未查询到相应的实例");
        }
        int currentStatus = TaskVersionStatus.SUBMITTED.getValue();
        int targetStatus = TaskVersionStatus.RUNNING.getValue();

        if (taskInstance.getStatus() != targetStatus) {
            Date now = DateUtils.getNow();

            TaskInstance updateInstance = new TaskInstance();
            updateInstance.setId(instanceId);
            updateInstance.setMsg(msg);
            updateInstance.setRunStartTime(now);
            updateInstance.setStatus(targetStatus);
            if (taskInstanceService.updateWithStatus(updateInstance, currentStatus) == 1) {

                TaskVersion taskVersion = taskVersionService.getById(taskInstance.getTaskVersionId());
                if (taskVersion == null) {
                    throw new ConsoleRuntimeException("未查询到相关的任务版本");
                }
                if (taskVersion.getStatus() == targetStatus) {
                    return;
                }

                TaskVersion updateVersion = new TaskVersion();
                updateVersion.setId(taskInstance.getTaskVersionId());
                updateVersion.setStatus(targetStatus);
                taskVersionService.updateWithStatus(updateVersion, currentStatus);
            } else {
                throw new ConsoleRuntimeException("更新实例状态失败");
            }
        }
    }

    @Transactional("consoleTxManager")
    @Override
    public void fail(long instanceId, String msg) throws ConsoleRuntimeException {
        TaskInstance taskInstance = taskInstanceService.getById(instanceId);
        if (taskInstance == null) {
            throw new ConsoleRuntimeException("未查询到相应的实例");
        }

        if (taskInstance.getStatus() == TaskVersionStatus.FAIL.getValue()) {
            return;
        }
        if (!(taskInstance.getStatus() == TaskVersionStatus.RUNNING.getValue()
                || taskInstance.getStatus() == TaskVersionStatus.SUBMITTED.getValue())) {
            throw new ConsoleRuntimeException("该实例状态是非执行中状态, 不能更新为失败状态");
        }

        int currentStatus = taskInstance.getStatus();
        int targetStatus = TaskVersionStatus.FAIL.getValue();

        Date now = DateUtils.getNow();
        TaskInstance updateInstance = new TaskInstance();
        updateInstance.setId(instanceId);
        updateInstance.setStatus(targetStatus);
        updateInstance.setRunEndTime(now);
        if (taskInstanceService.updateWithStatus(updateInstance, currentStatus) == 1) {

            Long taskVersionId = taskInstance.getTaskVersionId();
            TaskVersion taskVersion = taskVersionService.getById(taskVersionId);
            if (taskVersion == null) {
                throw new ConsoleRuntimeException("未获取到相关任务版本");
            }
            if (taskVersion.getStatus() == targetStatus) {
                return;
            }

            TaskVersion updateVersion = new TaskVersion();
            updateVersion.setId(taskVersionId);
            updateVersion.setStatus(targetStatus);
            updateVersion.setFinalStatus(TaskVersionFinalStatus.FAIL.getValue());
            taskVersionService.updateWithStatus(updateVersion, currentStatus);

            try {
                //失败重试
                taskVersionService.retry(taskVersionId);
            }catch (Throwable e){
                LOG.error("instance retry error: versionId:{}", taskVersion.getId(), e);
            }
        } else {
            throw new ConsoleRuntimeException("更新状态失败");
        }
    }

    @Transactional("consoleTxManager")
    @Override
    public void killed(long instanceId, String msg) throws ConsoleRuntimeException {
        TaskInstance taskInstance = taskInstanceService.getById(instanceId);
        if (taskInstance == null) {
            throw new ConsoleRuntimeException("未查询到相关的实例");
        }

        if (taskInstance.getStatus() == TaskVersionStatus.KILLED.getValue()) {
            return;
        }

        int currentStatus = TaskVersionStatus.RUNNING.getValue();
        int targetStatus = TaskVersionStatus.KILLED.getValue();

        Date now = DateUtils.getNow();
        TaskInstance updateInstance = new TaskInstance();
        updateInstance.setId(instanceId);
        updateInstance.setStatus(targetStatus);
        updateInstance.setRunEndTime(now);
        updateInstance.setMsg(msg);
        if (taskInstanceService.updateWithStatus(updateInstance, currentStatus) == 1) {

            TaskVersion taskVersion = taskVersionService.getById(taskInstance.getTaskVersionId());
            if (taskVersion == null) {
                throw new ConsoleRuntimeException("未获取到相关任务版本");
            }
            if (taskVersion.getStatus() == TaskVersionStatus.KILLED.getValue()) {
                return;
            }

            TaskVersion updateVersion = new TaskVersion();
            updateVersion.setId(taskInstance.getTaskVersionId());
            updateVersion.setStatus(targetStatus);
            updateVersion.setFinalStatus(TaskVersionFinalStatus.KILLED.getValue());
            taskVersionService.updateWithStatus(updateVersion, currentStatus);
        }

    }
}