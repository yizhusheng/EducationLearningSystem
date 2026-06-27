package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author YiZhuSheng
 * @since 2026-06-16
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {
    private final IInteractionReplyService replyService;
    private final UserClient userClient;
    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache  categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        //1.获取当前登录的用户
        Long userId = UserContext.getUser();
        //2.数据封装
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        //3.写入数据库
        save(question);

    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.参数校验,课程id和小节id不能都为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if(courseId == null && sectionId == null){
            throw new BadRequestException("课程id和小节id不能都为空");
        }
        //2.分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //3.根据id查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        //3.1.得到问题当中的提问者id和最近一次回答的id
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()){//只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        //3.2.根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)){
            List<InteractionReply> replies = replyService.listByIds(answerIds);
            for (InteractionReply reply : replies) {
                //前面userIds添加的时候是添加提问者id,目的是为了用提问者userId查询它的用户信息
                //用最近一次回答问题id查询到回答表,然后获取其用户id,在进行查询用户信息
                replyMap.put(reply.getId(), reply);
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());
                }
            }
        }
        //3.3.根据id查询用户信息(提问者)
        userIds.remove(null);
        Map<Long, UserDTO> userMap =  new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)){
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        //4.封装vo
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            //4.1.将po转为vo
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            voList.add(vo);
            //4.2.封装提问者信息
            UserDTO userDTO = userMap.get(r.getUserId());
            if(userDTO != null){
                //不为匿名用户
                vo.setUserName(userDTO.getUsername());
                vo.setUserIcon(userDTO.getIcon());
            }
            //4.3.封装最新一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if(reply != null){
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){
                    UserDTO user = userMap.get(reply.getUserId());
                    vo.setLatestReplyUser(user.getName());
                }
            }
        }


        return PageDTO.of(page, voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.根据id查询数据
        InteractionQuestion question = getById(id);
        //2.数据校验
        if(question == null || question.getHidden()){
            //没有数据或者数据被隐藏
            return null;
        }
        //3.查询提问者信息
        UserDTO user = new UserDTO();
        if (!question.getAnonymity()) {
            user = userClient.queryUserById(question.getUserId());
        }
        //4.转po为vo
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //1.处理课程名称,得到课程id
        List<Long> courseIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())){
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(courseIds)){
                return PageDTO.empty(0L, 0L);
            }
        }
        //2.分页查询
        Integer status = query.getStatus();
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(beginTime != null, InteractionQuestion::getCreateTime, beginTime)
                .le(endTime != null, InteractionQuestion::getCreateTime, endTime)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        //3.准备VO需要的数据:用户数据 课程数据 章节数据 (分类数据)(缓存)
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        //3.1.获取各种数据id的集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        //3.2.根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if(CollUtils.isNotEmpty(userMap)){
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        //3.3.根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long, CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if(CollUtils.isNotEmpty(cIds)){
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }
        //3.4.根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> catasMap = new HashMap<>(catas.size());
        if(CollUtils.isNotEmpty(catas)){
            catasMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        //封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            //4.1.将PO转VO,属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            //4.2.用户信息
            UserDTO user = userMap.get(q.getUserId());
            if(user != null){
                vo.setUserName(user.getName());
            }
            //4.3.课程信息及分类信息
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if(cInfo != null){
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            //4.4.章节信息
            vo.setChapterName(catasMap.getOrDefault(q.getChapterId(), ""));
            vo.setSectionName(catasMap.getOrDefault(q.getSectionId(), ""));
        }
        return PageDTO.of(page, voList);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        //1.获取当前用户
        Long useId = UserContext.getUser();
        //2.查询当前问题
        InteractionQuestion q = getById(id);
        if(q == null){
            throw new BadRequestException("问题不存在");
        }
        //3.判断是否是当前用户的问题
        if(!q.getUserId().equals(useId)){
            throw new BadRequestException("您没有权限修改别人该问题");
        }
        //修改问题
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setId(id);
        updateById(question);
    }

    @Override
    public void deleteById(Long id) {
        //获取用户 id
        Long useId = UserContext.getUser();
        //1.查询问题是否存在
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BadRequestException("问题不存在");
        }
        //2.判断是否是当前用户提问的
        if (!question.getUserId().equals(useId)){
            //2.1.如果不是则报错
            throw new BadRequestException("您没有权限删除别人该问题");
        }
        //4.如果是则删除问题
        removeById(id);
        //5.然后删除问题下的回答及评论
        replyService.removeByQuestionId(id);
    }

    @Override
    public void hiddenQuestion(Long id, Boolean hidden) {
        InteractionQuestion question = new InteractionQuestion();
        question.setId(id);
        question.setHidden(hidden);
        updateById(question);
    }

    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        //获取互动信息
        InteractionQuestion question = getById(id);
        if(question == null){
            return null;
        }
        //获取用户id
        Long userId = question.getUserId();
        //获取课程id
        Long courseId = question.getCourseId();
        //获取章id
        Long chapterId = question.getChapterId();
        //获取节id
        Long sectionId = question.getSectionId();
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //根据用户id查询用户信息
        UserDTO user = userClient.queryUserById(userId);
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        //根据课程id查询课程信息
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(courseId, false, true);
        if (courseInfo != null) {
            vo.setCourseName(courseInfo.getName());
            vo.setCategoryName(categoryCache.getCategoryNames(courseInfo.getCategoryIds()));
            //教师信息
            List<Long> teacherIds = courseInfo.getTeacherIds();
            List<UserDTO> teacherDTO = userClient.queryUserByIds(teacherIds);
            vo.setTeacherName(teacherDTO.stream().map(UserDTO::getName).collect(Collectors.joining("/")));
        }
        //根据章节id查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(List.of(chapterId, sectionId));
        Map<Long, String> catasMap = new HashMap<>(catas.size());
        if(CollUtils.isNotEmpty(catas)){
            catasMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        vo.setChapterName(catasMap.getOrDefault(chapterId, ""));
        vo.setSectionName(catasMap.getOrDefault(sectionId, ""));
        //如果未查看,更改信息为查看
        vo.setStatus(1);
        return vo;
    }
}
