package cn.lite.flow.console.service.auth;


import cn.lite.flow.common.model.consts.BooleanType;
import cn.lite.flow.console.common.enums.*;
import cn.lite.flow.console.model.basic.Flow;
import cn.lite.flow.console.model.basic.UserGroupAuthMid;
import cn.lite.flow.console.model.query.UserGroupAuthMidQM;
import cn.lite.flow.console.service.FlowService;
import cn.lite.flow.console.service.UserGroupAuthMidService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by ly on 2018/11/9.
 */
@Service("taskFlowAuthCheckServiceImpl")
public class TaskFlowAuthCheckServiceImpl implements AuthCheckService {

    @Autowired
    private UserGroupAuthMidService userGroupAuthMidService;

    @Autowired
    private FlowService flowService;

    @Override
    public AuthCheckTypeEnum supportCheckType() {
        return AuthCheckTypeEnum.AUTH_CHECK_FLOW;
    }

    @Override
    public boolean checkUserAuth(Long userId, Long targetId, OperateTypeEnum operateTypeEnum) {
        Flow flow = flowService.getById(targetId);
        if (flow.getUserId().equals(userId)) {
            /**默认用户拥有者有所有的权限*/
            return true;
        }
        UserGroupAuthMidQM userGroupAuthMidQM = new UserGroupAuthMidQM();
        userGroupAuthMidQM.setSourceId(userId);
        userGroupAuthMidQM.setTargetId(targetId);
        userGroupAuthMidQM.setSourceType(SourceTypeEnum.SOURCE_TYPE_USER.getCode());
        userGroupAuthMidQM.setTargetType(TargetTypeEnum.TARGET_TYPE_FLOW.getCode());
        List<UserGroupAuthMid> userGroupAuthMidList = userGroupAuthMidService.list(userGroupAuthMidQM);
        if (CollectionUtils.isNotEmpty(userGroupAuthMidList)) {
            switch (operateTypeEnum) {
                case OPERATE_TYPE_EXECUTE:
                    return userGroupAuthMidList.get(0).getHasExecuteAuth() == BooleanType.TRUE.getValue();
                case OPERATE_TYPE_EDIT:
                    return userGroupAuthMidList.get(0).getHasEditAuth() == BooleanType.TRUE.getValue();
            }
        }
        return false;
    }

    @Override
    public boolean checkUserGroupAuth(List<Long> userGroupIds, Long targetId, OperateTypeEnum operateTypeEnum) {
        if(CollectionUtils.isEmpty(userGroupIds)){
            return false;
        }
        UserGroupAuthMidQM userGroupAuthMidQM = new UserGroupAuthMidQM();
        userGroupAuthMidQM.setSourceIds(userGroupIds);
        userGroupAuthMidQM.setTargetId(targetId);
        userGroupAuthMidQM.setSourceType(SourceTypeEnum.SOURCE_TYPE_USER_GROUP.getCode());
        userGroupAuthMidQM.setTargetType(TargetTypeEnum.TARGET_TYPE_FLOW.getCode());
        List<UserGroupAuthMid> userGroupAuthMidList = userGroupAuthMidService.list(userGroupAuthMidQM);

        boolean hasAuth = false;
        if (CollectionUtils.isNotEmpty(userGroupAuthMidList)) {
            for (UserGroupAuthMid model : userGroupAuthMidList) {
                switch (operateTypeEnum) {
                    case OPERATE_TYPE_EXECUTE:
                        hasAuth = (model.getHasExecuteAuth() == BooleanType.TRUE.getValue());
                        break;
                    case OPERATE_TYPE_EDIT:
                        hasAuth = (model.getHasEditAuth() == BooleanType.TRUE.getValue());
                        break;
                    default:
                        break;
                }
                if (hasAuth) {
                    break;
                }
            }
        }
        return hasAuth;
    }
}
