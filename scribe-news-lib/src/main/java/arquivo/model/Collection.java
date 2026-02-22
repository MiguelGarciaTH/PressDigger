package arquivo.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean isPublic = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "collection_article",
        joinColumns = @JoinColumn(name = "collection_id"),
        inverseJoinColumns = @JoinColumn(name = "article_id")
    )
    private Set<Article> articles = new HashSet<>();

    private LocalDateTime createdAt;

    public Collection() {
    }

    public Collection(String name, User user, String description) {
        this.name = name;
        this.user = user;
        this.isPublic = false;
        this.description = description;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Collection(String name, String description, boolean isPublic) {
        this.name = name;
        this.description = description;
        this.user = null;
        this.isPublic = isPublic;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<Article> getArticles() {
        return articles;
    }

    public void setArticles(Set<Article> articles) {
        this.articles = articles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
