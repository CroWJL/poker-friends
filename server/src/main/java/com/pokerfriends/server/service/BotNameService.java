package com.pokerfriends.server.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class BotNameService {
  private static final List<String> NAME_POOL = List.of(
      "Ace", "Bluff王", "冷面杀手", "德州老炮", "All-in哥", "河牌猎人", "慢打大师", "翻牌狂魔",
      "筹码收割机", "底池小偷", "坚果玩家", "听牌小王子", "弃牌艺术家", "加注机器", "跟注侠",
      "鲨鱼Tom", "咸鱼Jack", "稳如老狗", "浪里白条", "铁头功", "读牌专家", "诈唬大师",
      "深筹玩家", "短码勇士", "位置狂魔", "范围怪", "价值下注", "过牌陷阱", "河杀高手",
      "翻前战神", "转牌杀手", "河牌收割", "小盲刺客", "大盲守卫", "按钮位霸主", "早位忍者",
      "晚位猎手", "全下狂人", "保守派", "激进派", "平衡大师", "GTO学徒", "运气选手",
      "冷静观察", "快速决策", "慢思考", "快思考", "牌桌幽灵", "沉默杀手", "话痨玩家",
      "微笑鲨鱼", "皱眉菜鸟", "墨镜哥", "帽子姐", "红衣战神", "蓝衣防守", "黑衣神秘",
      "Lucky7", "Pocket火箭", "同花顺梦", "葫芦娃", "四条大王", "皇家同花", "高牌侠客"
  );

  private final SecureRandom secureRandom = new SecureRandom();

  public List<String> randomBotNames(int count) {
    if (count <= 0) {
      return List.of();
    }
    List<String> pool = new ArrayList<>(NAME_POOL);
    Collections.shuffle(pool, secureRandom);
    if (count >= pool.size()) {
      return List.copyOf(pool.subList(0, count));
    }
    return List.copyOf(pool.subList(0, count));
  }
}
