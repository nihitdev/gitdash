package dev.nihit.gitdash.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ConfigEditor {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private ConfigEditor() {}
    public static void putAlias(Path file, String alias, Path repository) throws IOException {
        validate(alias); var lines=read(file); int start=section(lines,"aliases"), end=start<0?lines.size():end(lines,start);
        String value=alias+" = \""+repository.toAbsolutePath().normalize().toString().replace("\\","\\\\").replace("\"","\\\"")+"\"";
        if(start<0){if(!lines.isEmpty()&&!lines.getLast().isBlank())lines.add("");lines.add("[aliases]");lines.add(value);}
        else{for(int i=start+1;i<end;i++)if(key(lines.get(i),alias)){lines.set(i,value);write(file,lines);return;}lines.add(end,value);}write(file,lines);
    }
    public static boolean removeAlias(Path file,String alias)throws IOException{validate(alias);if(!Files.exists(file))return false;var lines=read(file);int start=section(lines,"aliases");if(start<0)return false;int end=end(lines,start);for(int i=start+1;i<end;i++)if(key(lines.get(i),alias)){lines.remove(i);write(file,lines);return true;}return false;}
    private static void validate(String alias){if(alias==null||!KEY.matcher(alias).matches())throw new IllegalArgumentException("Alias must contain only letters, digits, '_' or '-'");}
    private static boolean key(String line,String alias){return line.matches("\\s*"+Pattern.quote(alias)+"\\s*=.*");}
    private static int section(List<String> lines,String name){for(int i=0;i<lines.size();i++)if(lines.get(i).strip().equals("["+name+"]"))return i;return-1;}
    private static int end(List<String>lines,int start){for(int i=start+1;i<lines.size();i++)if(lines.get(i).strip().startsWith("["))return i;return lines.size();}
    private static ArrayList<String> read(Path file)throws IOException{return Files.exists(file)?new ArrayList<>(Files.readAllLines(file)):new ArrayList<>();}
    private static void write(Path file,List<String>lines)throws IOException{Files.createDirectories(file.getParent());Files.writeString(file,String.join(System.lineSeparator(),lines)+System.lineSeparator());}
}
