package com.savanna.backend.integration.contestkorea;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** UTF-8 RFC4180-style CSV, including quoted commas, double quotes and embedded newlines. */
public final class ContestKoreaCsv {
    private ContestKoreaCsv() {}
    public static List<Map<String,String>> read(Path path) throws IOException {
        String text=Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        if(text.startsWith("\uFEFF")) text=text.substring(1);
        var records=new ArrayList<List<String>>(); var row=new ArrayList<String>(); var cell=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='"') { if(quoted && i+1<text.length() && text.charAt(i+1)=='"'){cell.append('"');i++;} else quoted=!quoted; }
            else if(c==',' && !quoted){row.add(cell.toString());cell.setLength(0);}
            else if((c=='\n'||c=='\r')&&!quoted){ if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;
                row.add(cell.toString());cell.setLength(0); if(row.size()>1||!row.get(0).isBlank())records.add(List.copyOf(row)); row.clear(); }
            else cell.append(c);
        }
        if(quoted)throw new IllegalArgumentException("CSV_UNCLOSED_QUOTE");
        if(!row.isEmpty()||!cell.isEmpty()){row.add(cell.toString());records.add(List.copyOf(row));}
        if(records.isEmpty())throw new IllegalArgumentException("CSV_EMPTY");
        var headers=records.remove(0);
        if(new HashSet<>(headers).size()!=headers.size() || !headers.containsAll(List.of("api_id","category_source")))throw new IllegalArgumentException("CSV_HEADERS_INVALID");
        var result=new ArrayList<Map<String,String>>();
        for(var values:records){if(values.size()!=headers.size())throw new IllegalArgumentException("CSV_COLUMN_COUNT");
            var valuesByName=new LinkedHashMap<String,String>(); for(int i=0;i<headers.size();i++)valuesByName.put(headers.get(i),values.get(i).strip()); result.add(valuesByName);}
        return result;
    }
}
