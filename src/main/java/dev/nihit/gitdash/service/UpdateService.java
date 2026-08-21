package dev.nihit.gitdash.service;

import dev.nihit.gitdash.GitDash;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UpdateService {
    public int update(Path requestedPrefix) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path prefix = requestedPrefix == null ? inferPrefix(windows) : requestedPrefix.toAbsolutePath().normalize();
        Path directory = Files.createTempDirectory("gitdash-update-");
        Path installer = directory.resolve(windows ? "install.ps1" : "install.sh");
        URI uri = URI.create("https://github.com/nihitdev/gitdash/releases/latest/download/" + installer.getFileName());
        download(uri, installer, windows);
        var command = new ArrayList<String>();
        if (windows) { command.add("powershell.exe"); command.add("-NoProfile"); command.add("-ExecutionPolicy"); command.add("Bypass"); command.add("-File"); command.add(installer.toString()); command.add("-Prefix"); command.add(prefix.toString()); command.add("-WaitForProcessId"); command.add(Long.toString(ProcessHandle.current().pid())); }
        else { command.add("sh"); command.add(installer.toString()); }
        var process = new ProcessBuilder(command).inheritIO();
        if (!windows) process.environment().put("PREFIX", prefix.toString());
        var running = process.start();
        if (windows) { System.out.println("GitDash update scheduled; installation will continue after this process exits."); return 0; }
        int exit = running.waitFor();
        try { Files.deleteIfExists(installer); Files.deleteIfExists(directory); } catch (java.io.IOException ignored) { }
        return exit;
    }
    private static void download(URI uri,Path target,boolean windows)throws Exception{
        List<String> command;
        if(windows)command=List.of("powershell.exe","-NoProfile","-Command","Invoke-WebRequest -UseBasicParsing -Uri '"+uri+"' -OutFile '"+target.toString().replace("'","''")+"'");
        else if(available("curl"))command=List.of("curl","-fsSL","--retry","3","--connect-timeout","20",uri.toString(),"-o",target.toString());
        else if(available("wget"))command=List.of("wget","--tries=3","--timeout=20","-O",target.toString(),uri.toString());
        else throw new IllegalStateException("curl or wget is required to update GitDash");
        int exit=new ProcessBuilder(command).inheritIO().start().waitFor();if(exit!=0)throw new IllegalStateException("Cannot download the GitDash updater");
    }
    private static boolean available(String executable){try{return new ProcessBuilder(executable,"--version").redirectErrorStream(true).start().waitFor()==0;}catch(Exception ignored){return false;}}
    static Path inferPrefix(boolean windows) {
        try {
            Path location = Path.of(GitDash.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            Path lib = Files.isDirectory(location) ? location : location.getParent();
            if (lib != null && lib.getFileName().toString().equals("lib") && lib.getParent() != null && lib.getParent().getFileName().toString().equals("gitdash")) return lib.getParent().getParent().getParent();
        } catch (Exception ignored) { }
        if (windows) return Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "Programs", "GitDash");
        return Path.of(System.getProperty("user.home"), ".local");
    }
}
