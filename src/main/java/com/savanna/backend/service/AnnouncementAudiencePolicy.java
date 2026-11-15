package com.savanna.backend.service;

import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/** Only explicit youth-only eligibility is excluded. A title is never exclusion evidence. */
@Component
public class AnnouncementAudiencePolicy {
    public static final String VERSION="YOUTH_ONLY_V1";
    public static final String EXCLUDE_REASON="[YOUTH_ONLY] 초·중·고생/청소년 전용 공고";
    private static final Set<String> YOUTH=Set.of("유치원","유치원생","어린이","아동","청소년","청소년전용","청소년만",
            "초","중","고","초등","중등","고등","초등학생","중학생","고등학생","초중고생","초중고학생","중고생","중고등학생");
    private static final Set<String> OPEN_OR_ADULT=Set.of("누구나","제한없음","연령제한없음","나이제한없음","전국민",
            "대학생","대학원생","일반인","성인","청년");
    private static final Pattern YOUTH_MENTION=Pattern.compile("청소년|어린이|초등|중학생|고등학생|중고생|초중고");
    public enum Action { KEEP, EXCLUDE, REVIEW }
    public record Decision(Action action,String reason,boolean inclusiveAudience) {}

    public Decision assess(String title,String target) {
        boolean youthTitle=title!=null&&YOUTH_MENTION.matcher(title).find();
        if(target==null||target.isBlank())return new Decision(youthTitle?Action.REVIEW:Action.KEEP,"참가 대상 미확인; 제목으로 제외하지 않음",false);
        var tokens=Arrays.stream(target.replaceAll("\\s+","").split("[,，/|·•ㆍ]+|및|또는"))
                .filter(s->!s.isBlank()).toList();
        if(tokens.stream().anyMatch(OPEN_OR_ADULT::contains)) {
            return new Decision(youthTitle?Action.REVIEW:Action.KEEP,"누구나·대학생·일반인 등 포함; 청소년 전용으로 분류하지 않음",true);
        }
        if(!tokens.isEmpty()&&tokens.stream().allMatch(YOUTH::contains))return new Decision(Action.EXCLUDE,EXCLUDE_REASON,false);
        boolean youthTarget=YOUTH_MENTION.matcher(target).find();
        return new Decision(youthTitle||youthTarget?Action.REVIEW:Action.KEEP,"대상 원문이 단순 청소년 전용 목록으로 확정되지 않음",false);
    }
}
