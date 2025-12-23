package arquivo.model;

import jakarta.persistence.*;

@Entity
public class UrlLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Site site;

    @ManyToOne(fetch = FetchType.EAGER)
    private Keyword keyword;

    private int offset;

    public UrlLog(){

    }

    public UrlLog(Site site, Keyword keyword, int offset){
        this.site = site;
        this.keyword = keyword;
        this.offset = offset;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public int getId() {
        return id;
    }

    public Keyword getKeyword() {
        return keyword;
    }

    public void setKeyword(Keyword keyword) {
        this.keyword = keyword;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
