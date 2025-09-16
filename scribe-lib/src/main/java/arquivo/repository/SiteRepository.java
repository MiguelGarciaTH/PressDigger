package arquivo.repository;

import arquivo.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Query("""
            select new arquivo.repository.SiteRepository$SiteCounter(a.site.id, a.site.name, count(a.id))
            from Article a
            where a.contextualScore > 0
            group by (a.site.id, a.site.name)
            order by a.site.name asc
            """)
    List<SiteRepository.SiteCounter> getSiteCounts();


    record SiteCounter(int id, String name, Long count) {

    }
}
