package com.fishmoun.soulroute;

import com.fishmoun.soulroute.app.TravelApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
@SpringBootTest
class TravelAppTest {

    @Resource
    private TravelApp travelApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();

        // 第一轮：用户提供基础身份和出行需求
        String message = "你好，我是林凡，想在五一假期去成都玩 3 天，预算 3000 元。";
        String answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        System.out.println("第一轮回答：" + answer);

        // 第二轮：补充出行偏好，验证智能体能继续追问或生成更具体建议
        message = "我会和另一半于珊一起去，想吃美食、逛热门景点，不想行程太赶。";
        answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        System.out.println("第二轮回答：" + answer);

        // 第三轮：验证记忆能力，检查是否记得同行人信息
        message = "我的另一半叫什么来着？刚跟你说过，帮我回忆一下。";
        answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        System.out.println("第三轮回答：" + answer);

        // 可选：进一步断言是否正确记住上下文
        Assertions.assertTrue(answer.contains("于珊"));
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();

        // 第一轮：提供基础旅行需求
        String message = "你好，我是林凡，计划和另一半于珊一起去成都玩 3 天，预算 3000 元，想吃美食、逛景点，行程轻松一点。";
        TravelApp.TravelReport travelReport = travelApp.doChatWithReport(message, chatId);

        Assertions.assertNotNull(travelReport);
        System.out.println("旅行报告：" + travelReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是林凡，计划和另一半于珊一起去成都玩 3 天，预算 3000 元，想吃美食、逛景点，行程轻松一点。";
        String answer =  travelApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想去上海玩两天，推荐几个适合拍照、人少景美的小众打卡地？");

// 测试网页抓取：旅行经验案例分析
        testMessage("第一次去云南自由行有点没头绪，帮我看看马蜂窝网站（mafengwo.cn）上的真实游记，大家一般怎么规划路线和预算？");

// 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的星空旅行风景图片为文件");

// 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成旅行数据分析报告");

// 测试文件操作：保存用户档案
        testMessage("保存我的旅行偏好档案为文件");

// 测试 PDF 生成
        testMessage("生成一份‘三天两夜上海旅行计划’PDF，包含住宿安排、每日行程和预算清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = travelApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }


    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
        String message = "帮我搜一些上海景点的图片";
        String answer =  travelApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }


}
