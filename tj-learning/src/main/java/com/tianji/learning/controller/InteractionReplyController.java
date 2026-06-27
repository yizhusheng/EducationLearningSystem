package com.tianji.learning.controller;


import cn.hutool.db.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author YiZhuSheng
 * @since 2026-06-16
 */
@RestController
@RequestMapping("/replies")
@Api(tags = "互动问答相关接口")
@RequiredArgsConstructor
public class InteractionReplyController {
    private final IInteractionReplyService replyService;


    @PostMapping
    @ApiOperation("新增回答或评论")
    public void saveReply(@RequestBody ReplyDTO replyDTO){
        replyService.saveReply(replyDTO);
    }

    @ApiOperation("分页查询回答或评论")
    @GetMapping("page")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery, boolean forAdmin){
        return replyService.queryReplyPage(pageQuery, forAdmin);
    }
}
