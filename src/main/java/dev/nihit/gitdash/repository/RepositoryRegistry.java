package dev.nihit.gitdash.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.nihit.gitdash.model.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class RepositoryRegistry {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).enable(SerializationFeature.INDENT_OUTPUT);
    public RepositoryRegistry(Path stateDir) { this.file = stateDir.resolve("repositories.json"); }

    public synchronized List<Repository> load() throws IOException {
        if (!Files.exists(file)) return List.of();
        try { var tree=mapper.readTree(file.toFile());var out=new ArrayList<Repository>();for(var n:tree)out.add(new Repository(n.get("name").asText(),Path.of(n.get("path").asText()),java.time.Instant.parse(n.get("discoveredAt").asText())));return List.copyOf(out); }
        catch (IOException e) { throw new IOException("Cannot read repository registry " + file + ": " + e.getMessage(), e); }
    }
    public synchronized List<Repository> merge(List<Repository> additions) throws IOException {
        var byPath = new LinkedHashMap<Path, Repository>();
        for (Repository r : load()) byPath.put(r.path(), r);
        for (Repository r : additions) byPath.put(r.path(), r);
        var all = new ArrayList<>(byPath.values()); all.sort(java.util.Comparator.comparing(r -> r.path().toString()));
        save(all); return List.copyOf(all);
    }
    public synchronized boolean remove(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        var repositories = new ArrayList<>(load());
        boolean changed = repositories.removeIf(repository -> repository.path().equals(normalized));
        if (changed) save(repositories);
        return changed;
    }
    public synchronized List<Repository> removeMissing() throws IOException {
        var repositories = new ArrayList<>(load());
        var removed = repositories.stream().filter(repository -> !Files.isDirectory(repository.path())).toList();
        if (!removed.isEmpty()) { repositories.removeAll(removed); save(repositories); }
        return removed;
    }
    private void save(List<Repository> repositories) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "repositories-", ".tmp");
        var rows=repositories.stream().map(r->java.util.Map.of("name",r.name(),"path",r.path().toString(),"discoveredAt",r.discoveredAt().toString())).toList();
        mapper.writeValue(temp.toFile(), rows);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    public Path file() { return file; }
}
