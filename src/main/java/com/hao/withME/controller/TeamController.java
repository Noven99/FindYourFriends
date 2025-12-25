package com.hao.withME.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hao.withME.common.BaseResponse;
import com.hao.withME.common.DeleteRequest;
import com.hao.withME.common.ErrorCode;
import com.hao.withME.common.ResultUtils;
import com.hao.withME.exception.BusinessException;
import com.hao.withME.model.domain.Team;
import com.hao.withME.model.domain.User;
import com.hao.withME.model.domain.UserTeam;
import com.hao.withME.model.dto.TeamQuery;
import com.hao.withME.model.request.TeamAddRequest;
import com.hao.withME.model.request.TeamJoinRequest;
import com.hao.withME.model.request.TeamQuitRequest;
import com.hao.withME.model.request.TeamUpdateRequest;
import com.hao.withME.model.vo.TeamUserVO;
import com.hao.withME.service.TeamService;
import com.hao.withME.service.UserService;
import com.hao.withME.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 队伍接口
 */
@RestController
@RequestMapping("/team")
@CrossOrigin(origins = {"http://localhost:3000"})
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
        long teamId = teamService.addTeam(team, loginUser);
        return ResultUtils.success(teamId);
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }

    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);

        // 1. 查询队伍列表
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
        final List<Long> teamIdList = teamList.stream()
                .map(TeamUserVO::getId)
                .collect(Collectors.toList());

        // 如果一个队伍都没有，后面逻辑都不用查 DB 了，直接返回空列表
        if (teamIdList.isEmpty()) {
            // hasJoin 默认就是 false，hasJoinNum 默认 0 的话，这里可以直接返回
            return ResultUtils.success(teamList);
        }

        // 2. 判断当前用户是否已加入队伍
        try {
            User loginUser = userService.getLoginUser(request);

            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);  // 此时 teamIdList 一定非空
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);

            // 已加入的队伍 id 集合
            Set<Long> hasJoinTeamIdSet = userTeamList.stream()
                    .map(UserTeam::getTeamId)
                    .collect(Collectors.toSet());

            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception e) {
            // 建议至少打个日志，避免完全吃异常
            // log.error("listTeams - set hasJoin error", e);
        }

        // 3. 查询已加入队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);

        // 队伍 id => 加入这个队伍的用户列表
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));

        teamList.forEach(team -> {
            int joinNum = teamIdUserTeamList
                    .getOrDefault(team.getId(), new ArrayList<>())
                    .size();
            team.setHasJoinNum(joinNum);
        });

        return ResultUtils.success(teamList);
    }

    // todo 查询分页
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQuery teamQuery) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        BeanUtils.copyProperties(teamQuery, team);
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> resultPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(resultPage);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(id, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }


    /**
     * 获取我创建的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        teamQuery.setUserId(loginUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }


    /**
     * 获取我加入的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);

        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);

        // 如果当前用户一个队伍都没加入，直接返回空列表即可
        if (userTeamList.isEmpty()) {
            return ResultUtils.success(new ArrayList<>());
        }

        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);

        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        //  如果根据 team 条件筛完后没有任何队伍了，直接返回，避免后面 in ()
        if (teamList == null || teamList.isEmpty()) {
            return ResultUtils.success(new ArrayList<>());
        }


        teamList.forEach(team -> team.setHasJoin(true));


        //统计每个队伍加入的人数
        List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());

        if (teamIdList == null || teamIdList.isEmpty()) {
            return ResultUtils.success(teamList);
        }

        QueryWrapper<UserTeam> joinQueryWrapper = new QueryWrapper<>();
        joinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> allJoinList = userTeamService.list(joinQueryWrapper);

        Map<Long, List<UserTeam>> teamIdUserTeamList = allJoinList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));

        teamList.forEach(team -> {
            int joinNum = teamIdUserTeamList
                    .getOrDefault(team.getId(), new ArrayList<>())
                    .size();
            team.setHasJoinNum(joinNum);
        });


        return ResultUtils.success(teamList);
    }
}



























