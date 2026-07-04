package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author YiZhuSheng
 * @since 2026-06-27
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {
    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        //1.判断是否查询当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        //2.获取redis的key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        //2.查询我的积分
        PointsBoard myBoard = isCurrent ?
                //查询寻当前榜单(redis)
                queryMyCurrentBoard(key) :
                //查询历史榜单(mysql)
                queryHistoryBoard(season);
        //3.查询榜单列表
        List<PointsBoard> list = isCurrent ?
                //查询当前榜单(mysql)
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) :
                //查询历史榜单(mysql)
                queryHistoryBoardList(query);
        //4.封装vo
        PointsBoardVO vo = new PointsBoardVO();
        //4.1.处理我的信息
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        if(CollUtils.isEmpty( list)){
            return vo;
        }
        //4.2.处理用户信息
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(uIds);
        Map<Long, String> userMap = new HashMap<>(uIds.size());
        if(CollUtils.isNotEmpty(users)){
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        //4.3.处理榜单信息
        List<PointsBoardItemVO> items = new ArrayList<>(list.size());
        for (PointsBoard p : list) {
            PointsBoardItemVO v = new PointsBoardItemVO();
            v.setPoints(p.getPoints());
            v.setRank(p.getRank());
            v.setName(userMap.get(p.getUserId()));
            items.add(v);
        }
        vo.setBoardList(items);
        return vo;
    }

    @Override
    @Transactional
    public void createPointsBoardTableBySeason(Integer season) {
        getBaseMapper().createPointsBoardTable("points_board_" + season);
    }

    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        return null;
    }

    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, @Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        //1.计算分页
        int from = (pageNo - 1) * pageSize;
        //2.查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(typedTuples)){
            return CollUtils.emptyList();
        }
        //3.封装
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples){
            String userId = tuple.getValue();
            Double score = tuple.getScore();
            if(userId == null || score == null){
                continue;
            }
            PointsBoard p = new PointsBoard();
            p.setUserId(Long.valueOf(userId));
            p.setPoints(score.intValue());
            p.setRank(rank++);
            list.add(p);
        }
        return list;
    }

    private PointsBoard queryHistoryBoard(Long season) {




        return null;
    }

    private PointsBoard queryMyCurrentBoard(String key) {
        //1.绑定key
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        //2.获取当前用户信息
        String userId = UserContext.getUser().toString();
        //3.查询积分
        Double points = ops.score(userId);
        //4.查询排名
        Long rank = ops.reverseRank(userId);
        //5.封装返回
        PointsBoard po = new PointsBoard();
        po.setPoints(points == null ? 0 : points.intValue());
        po.setRank(rank == null ? 0 : rank.intValue() + 1);
        return po;
    }
}
