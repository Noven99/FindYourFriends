package com.hao.withME.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hao.withME.common.ErrorCode;
import com.hao.withME.exception.BusinessException;
import com.hao.withME.model.domain.Team;
import com.hao.withME.model.domain.User;
import com.hao.withME.model.domain.UserTeam;
import com.hao.withME.model.dto.TeamQuery;
import com.hao.withME.model.enums.TeamStatusEnum;
import com.hao.withME.model.request.TeamJoinRequest;
import com.hao.withME.model.request.TeamQuitRequest;
import com.hao.withME.model.request.TeamUpdateRequest;
import com.hao.withME.model.vo.TeamUserVO;
import com.hao.withME.model.vo.UserVO;
import com.hao.withME.service.TeamService;
import com.hao.withME.mapper.TeamMapper;
import com.hao.withME.service.UserService;
import com.hao.withME.service.UserTeamService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
* @author 86182
* @description 针对表【team(队伍)】的数据库操作Service实现
* @createDate 2025-12-22 20:18:38
*/
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
    implements TeamService{


    @Resource
    private UserTeamService userTeamService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(Team team, User loginUser) {
        // 1. 请求参数是否为空？
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 是否登录，未登录不允许创建
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        final long userId = loginUser.getId();
        // 3. 校验信息
        //   1. 队伍人数 > 1 且 <= 20
        int maxNum = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxNum < 1 || maxNum > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "The team does not meet the number of participants");
        }
        //   2. 队伍标题 <= 20
        String name = team.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Team title does not meet requirements");
        }
        //   3. 描述 <= 512
        String description = team.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Team description too long");
        }
        //   4. status 是否公开（int）不传默认为 0（公开）
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "The team's status does not meet the requirements");
        }
        //   5. 如果 status 是加密状态，一定要有密码，且密码 <= 32
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum)) {
            if (StringUtils.isBlank(password) || password.length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "Incorrect password");
            }
        }
        // 6. 超时时间 > 当前时间
        Date expireTime = team.getExpireTime();
        if (new Date().after(expireTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Timeout > Current Time");
        }

        // 7. 校验用户最多创建和加入 5 个队伍
        // --- 修改开始：统计逻辑改为查 UserTeam 表 ---

        // 7.1 先从 UserTeam 表查出该用户关联的所有队伍ID
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("userId", userId);
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);

        // 7.2 统计有效队伍数量
        long hasTeamNum = 0;
        if (userTeamList != null && !userTeamList.isEmpty()) {
            // 收集所有队伍ID
            Set<Long> teamIds = userTeamList.stream()
                    .map(UserTeam::getTeamId)
                    .collect(Collectors.toSet());

            // 7.3 去 Team 表查询这些 ID 对应的队伍，并且过滤掉过期的
            QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
            teamQueryWrapper.in("id", teamIds);
            // 过滤条件：过期时间为空 OR 过期时间 > 当前时间
            teamQueryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));

            hasTeamNum = this.count(teamQueryWrapper);
        }

        if (hasTeamNum >= 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Users can create and join a maximum of 5 teams");
        }

        // 8. 插入队伍信息到队伍表
        team.setId(null);
        team.setUserId(userId);
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Team creation failed");
        }
        // 9. 插入用户  => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(userId);
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Team creation failed");
        }
        return teamId;
    }

    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdmin) {
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();

        // 1. 在方法内部获取当前登录用户
        User loginUser = null;
        try {
            // 通过 Spring 上下文获取 Request
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            // 获取当前用户 (如果未登录可能会抛异常或返回null，视具体实现而定)
            if (request != null) {
                loginUser = userService.getLoginUser(request);
            }
        } catch (Exception e) {
            // 未登录或获取失败，loginUser 保持为 null，不影响后续逻辑（只是查不到私有队伍）
        }

        // 组合查询条件
        if (teamQuery != null) {
            Long id = teamQuery.getId();
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }
            List<Long> idList = teamQuery.getIdList();
            if (CollectionUtils.isNotEmpty(idList)) {
                queryWrapper.in("id", idList);
            }
            String searchText = teamQuery.getSearchText();
            if (StringUtils.isNotBlank(searchText)) {
                queryWrapper.and(qw -> qw.like("name", searchText).or().like("description", searchText));
            }
            String name = teamQuery.getName();
            if (StringUtils.isNotBlank(name)) {
                queryWrapper.like("name", name);
            }
            String description = teamQuery.getDescription();
            if (StringUtils.isNotBlank(description)) {
                queryWrapper.like("description", description);
            }
            Integer maxNum = teamQuery.getMaxNum();
            // 查询最大人数相等的
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxNum", maxNum);
            }
            Long userId = teamQuery.getUserId();
            // 根据创建人来查询
            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }

            List<Integer> statusList = teamQuery.getStatusList();
            Integer status = teamQuery.getStatus();

            // 场景 A: 前端传了 statusList (比如 Private Tab 传了 [1, 2])
            if (CollectionUtils.isNotEmpty(statusList)) {
                User finalLoginUser = loginUser; // lambda 表达式需要 final 变量

                queryWrapper.and(qw -> {
                    // 1. 处理加密房间 (Status = 2) -> 所有人可见
                    if (statusList.contains(TeamStatusEnum.SECRET.getValue())) {
                        qw.or().eq("status", TeamStatusEnum.SECRET.getValue());
                    }

                    // 2. 处理私有房间 (Status = 1) -> 仅自己可见
                    if (statusList.contains(TeamStatusEnum.PRIVATE.getValue())) {
                        // 如果未登录，根本查不到私有房间，所以必须 loginUser != null
                        if (finalLoginUser != null) {
                            qw.or(subQw -> {
                                subQw.eq("status", TeamStatusEnum.PRIVATE.getValue());
                                // 如果不是管理员，只能看自己创建的
                                if (!isAdmin) {
                                    subQw.eq("userId", finalLoginUser.getId());
                                }
                            });
                        } else if (isAdmin) {
                            // 如果是管理员但没获取到 loginUser 对象(罕见)，也可以看私有
                            qw.or().eq("status", TeamStatusEnum.PRIVATE.getValue());
                        }
                    }

                    // 3. 处理公开房间 (Status = 0)
                    if (statusList.contains(TeamStatusEnum.PUBLIC.getValue())) {
                        qw.or().eq("status", TeamStatusEnum.PUBLIC.getValue());
                    }
                });
            }
            // 场景 B: 兼容旧逻辑 (只传 status)
            else if (status != null && status > -1) {
                TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
                if (statusEnum == null) statusEnum = TeamStatusEnum.PUBLIC;

                if (statusEnum.equals(TeamStatusEnum.PRIVATE)) {
                    // 查私有：必须登录且是自己的
                    if (loginUser != null) {
                        queryWrapper.eq("status", TeamStatusEnum.PRIVATE.getValue());
                        if (!isAdmin) {
                            queryWrapper.eq("userId", loginUser.getId());
                        }
                    } else {
                        // 未登录查私有，直接让它查不到
                        queryWrapper.eq("status", -1);
                    }
                } else {
                    queryWrapper.eq("status", statusEnum.getValue());
                }
            }
        }
        // 不展示已过期的队伍
        // expireTime is null or expireTime > now()
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));
        List<Team> teamList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(teamList)) {
            return new ArrayList<>();
        }
        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        // 关联查询创建人的用户信息
        for (Team team : teamList) {
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }
            User user = userService.getById(userId);
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team, teamUserVO);
            // 脱敏用户信息
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                teamUserVO.setCreateUser(userVO);
            }
            teamUserVOList.add(teamUserVO);
        }
        return teamUserVOList;
    }

    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = teamUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team oldTeam = this.getById(id);
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "The team does not exist");
        }
        // 只有管理员或者队伍的创建者可以修改
        if (oldTeam.getUserId() != loginUser.getId() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if (statusEnum.equals(TeamStatusEnum.SECRET)) {
            if (StringUtils.isBlank(teamUpdateRequest.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "Encrypted rooms must be password protected");
            }
        }
        Team updateTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest, updateTeam);
        return this.updateById(updateTeam);
    }

    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamJoinRequest.getTeamId();
        Team team = getTeamById(teamId);
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "The team has expired");
        }
        Integer status = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Joining private teams is prohibited");
        }
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "Incorrect password");
            }
        }
        // 该用户已加入的队伍数量
        long userId = loginUser.getId();
        // 只有一个线程能获取到锁
        RLock lock = redissonClient.getLock("yupao:join_team");
        try {
            // 抢到锁并执行
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    System.out.println("getLock: " + Thread.currentThread().getId());

                    // --- 修改开始：统计已加入的队伍数量（排除过期和解散的） ---
                    // 1. 查出用户加入的所有 UserTeam 关系
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);

                    // 2. 过滤掉已过期或已解散的队伍
                    // 如果 userTeamList 为空，说明没加入任何队伍，count 为 0
                    long hasJoinNum = 0;
                    if (userTeamList != null && !userTeamList.isEmpty()) {
                        // 获取所有 teamId 并去重
                        Set<Long> teamIds = userTeamList.stream()
                                .map(UserTeam::getTeamId)
                                .collect(Collectors.toSet());

                        // 查询 Team 表，条件：id 在列表里 && (未过期 OR 无过期时间)
                        // 注意：MyBatis Plus 的 count() 默认会过滤掉逻辑删除（已解散）的记录
                        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
                        teamQueryWrapper.in("id", teamIds);
                        teamQueryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));

                        hasJoinNum = this.count(teamQueryWrapper);
                    }

                    if (hasJoinNum >= 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "Users can create and join a maximum of 5 teams");
                    }
                    // --- 修改结束 ---

                    // 不能重复加入已加入的队伍
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    userTeamQueryWrapper.eq("teamId", teamId);
                    long hasUserJoinTeam = userTeamService.count(userTeamQueryWrapper);
                    if (hasUserJoinTeam > 0) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "The user has joined the team");
                    }
                    // 已加入队伍的人数
                    long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
                    if (teamHasJoinNum >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "The queue is full");
                    }
                    // 修改队伍信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            return false;
        } finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamQuitRequest.getTeamId();
        Team team = getTeamById(teamId);
        long userId = loginUser.getId();
        UserTeam queryUserTeam = new UserTeam();
        queryUserTeam.setTeamId(teamId);
        queryUserTeam.setUserId(userId);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>(queryUserTeam);
        long count = userTeamService.count(queryWrapper);
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Not joined the team");
        }
        long teamHasJoinNum = this.countTeamUserByTeamId(teamId);
        // 队伍只剩一人，解散
        if (teamHasJoinNum == 1) {
            // 删除队伍
            this.removeById(teamId);
        } else {
            // 队伍还剩至少两人
            // 是队长
            if (team.getUserId() == userId) {
                // 把队伍转移给最早加入的用户
                // 1. 查询已加入队伍的所有用户和加入时间
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.eq("teamId", teamId);
                userTeamQueryWrapper.last("order by id asc limit 2");
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                if (CollectionUtils.isEmpty(userTeamList) || userTeamList.size() <= 1) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
                UserTeam nextUserTeam = userTeamList.get(1);
                Long nextTeamLeaderId = nextUserTeam.getUserId();
                // 更新当前队伍的队长
                Team updateTeam = new Team();
                updateTeam.setId(teamId);
                updateTeam.setUserId(nextTeamLeaderId);
                boolean result = this.updateById(updateTeam);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Team leader update failed");
                }
            }
        }
        // 移除关系
        return userTeamService.remove(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(long id, User loginUser) {
        // 校验队伍是否存在
        Team team = getTeamById(id);
        long teamId = team.getId();
        // 校验你是不是队伍的队长
        if (team.getUserId() != loginUser.getId()) {
            throw new BusinessException(ErrorCode.NO_AUTH, "No access permission");
        }
        // 移除所有加入队伍的关联信息
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        boolean result = userTeamService.remove(userTeamQueryWrapper);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Deleting team association information failed");
        }
        // 删除队伍
        return this.removeById(teamId);
    }


    /**
     * 根据 id 获取队伍信息
     *
     * @param teamId
     * @return
     */
    private Team getTeamById(Long teamId) {
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "The team does not exist");
        }
        return team;
    }

    /**
     * 获取某队伍当前人数
     *
     * @param teamId
     * @return
     */
    private long countTeamUserByTeamId(long teamId) {
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }


}




