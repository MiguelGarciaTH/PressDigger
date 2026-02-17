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
    private Person person;

    private int offset;

    public UrlLog(){

    }

    public UrlLog(Site site, Person person, int offset){
        this.site = site;
        this.person = person;
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

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
