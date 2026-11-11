package com.savanna.backend.integration.contestkorea;

import com.savanna.backend.domain.Announcement;
import com.savanna.backend.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ContestKoreaCsvWriter {
    private final AnnouncementRepository announcements;
    private final SourceAudit audit;
    public record Result(int rows,int created,int updated,int excluded,int unmatched,int failed,int dateConflicts,List<String> issues) {}

    @Transactional
    public Result apply(List<Map<String,String>> rows,String sourceLabel) {
        int created=0,updated=0,excluded=0,unmatched=0,failed=0,conflicts=0;
        var issues=new ArrayList<String>(); var seen=new HashSet<String>();
        var duplicates=rows.stream().collect(java.util.stream.Collectors.groupingBy(r->r.getOrDefault("api_id",""),java.util.stream.Collectors.counting()));
        int line=1;
        for(var original:rows){line++;var row=new LinkedHashMap<>(original);
            row.replaceAll((key,value)->value==null||Set.of("원문 참고","원문참고","원문 확인","공고 원문을 확인해 주세요").contains(value.strip())?"":value.strip());
            String id=row.get("api_id");
            var metadata=new LinkedHashMap<String,Object>();metadata.put("file",sourceLabel);metadata.put("row",line);metadata.put("values",original);
            var existing=id==null?Optional.<Announcement>empty():announcements.findBySourceProviderAndExternalId("CONTEST_KOREA",id);
            var category=ContestKoreaCategory.fromSource(row.get("category_source"));
            if(category.isEmpty()){
                unmatched++; issues.add(id+":UNSUPPORTED_CATEGORY:"+row.get("category_source"));
                existing.ifPresent(a->a.flagCsvCategory(row.get("category_source"),row.get("exclude_reason"),JsonMapper.builder().build().writeValueAsString(metadata)));
                audit.record(existing.orElse(null),id,"CSV","UNMATCHED",metadata); continue;
            }
            LocalDate start,end;
            try {
                if(id==null||id.isBlank()||id.length()>100||duplicates.get(id)>1||!seen.add(id))throw new IllegalArgumentException();
                start=date(row.get("apply_start"));end=date(row.get("apply_end"));
                LocalDate effectiveStart=start!=null?start:existing.map(Announcement::getApplicationStartsAt).map(java.time.LocalDateTime::toLocalDate).orElse(null);
                LocalDate effectiveEnd=end!=null?end:existing.map(Announcement::getDeadline).orElse(null);
                if(effectiveStart!=null&&effectiveEnd!=null&&effectiveEnd.isBefore(effectiveStart))throw new IllegalArgumentException();
                Integer min=number(row.get("team_min")),max=number(row.get("team_max"));
                Integer effectiveMin=min!=null?min:existing.map(Announcement::getMinMembers).orElse(null);
                Integer effectiveMax=max!=null?max:existing.map(Announcement::getMaxMembers).orElse(null);
                if(effectiveMin!=null&&effectiveMax!=null&&effectiveMin>effectiveMax)throw new IllegalArgumentException();
                if(existing.isEmpty() && (start==null||end==null||blank(row,"title")||blank(row,"organizer")||!url(row.get("source_url"))))throw new IllegalArgumentException();
                if(!blank(row,"title")&&row.get("title").length()>300 || !blank(row,"organizer")&&row.get("organizer").length()>255)throw new IllegalArgumentException();
            }catch(RuntimeException exception){failed++;issues.add(id+":INVALID_REQUIRED_VALUES_OR_DATES");
                if(!blank(row,"exclude_reason")){existing.ifPresent(a->a.supplement(Map.of("exclude_reason",row.get("exclude_reason")),null,null,JsonMapper.builder().build().writeValueAsString(metadata)));excluded++;}
                audit.record(existing.orElse(null),id,"CSV","INVALID_ROW",metadata);continue;}
            var announcement=existing.orElseGet(()->Announcement.builder().build());
            if(existing.isEmpty())announcement.initializeFromCsv(id,row.get("title"),row.get("organizer"),row.get("category_source"),row.get("source_url"),empty(row.get("poster_url")));
            if((start!=null&&announcement.getApplicationStartsAt()!=null&&!start.equals(announcement.getApplicationStartsAt().toLocalDate()))
                    ||(end!=null&&announcement.getDeadline()!=null&&!end.equals(announcement.getDeadline()))){
                conflicts++;issues.add(id+":DATE_CONFLICT");metadata.put("previousStart",String.valueOf(announcement.getApplicationStartsAt()));metadata.put("previousDeadline",String.valueOf(announcement.getDeadline()));
                metadata.put("previousDateSource",String.valueOf(announcement.getDateSource()));metadata.put("selectedDateSource","CSV");
            }
            announcement.recordCsvCategory(category.get().code());
            announcement.supplement(row,start,end,JsonMapper.builder().build().writeValueAsString(metadata));
            announcements.save(announcement);
            audit.record(announcement,id,"CSV","SUPPLEMENT",metadata);
            if(existing.isPresent())updated++;else created++;
            if(!blank(row,"exclude_reason"))excluded++;
            if(announcement.getClassificationIssue()!=null)issues.add(id+":"+announcement.getClassificationIssue());
            if(!blank(row,"required_region"))issues.add(id+":REGION_MEANING_REQUIRES_REVIEW");
        }
        return new Result(rows.size(),created,updated,excluded,unmatched,failed,conflicts,List.copyOf(issues));
    }
    private static LocalDate date(String s){return s==null||s.isBlank()?null:LocalDate.parse(s);}
    private static Integer number(String s){if(s==null||s.isBlank())return null;int n=Integer.parseInt(s);if(n<1)throw new IllegalArgumentException();return n;}
    private static boolean blank(Map<String,String> row,String key){return row.get(key)==null||row.get(key).isBlank();}
    private static String empty(String value){return value==null||value.isBlank()?null:value;}
    private static boolean url(String value){try{var uri=java.net.URI.create(value);return uri.getHost()!=null&&Set.of("http","https").contains(uri.getScheme());}catch(RuntimeException e){return false;}}
}
