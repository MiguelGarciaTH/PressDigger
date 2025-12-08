package arquivo.repository;

import arquivo.model.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


public interface UrlRepository extends JpaRepository<Url, Integer> {

    @Query(value = """
            SELECT u
            FROM Url u
            WHERE u.processed = false
            """)
    List<Url> getAllUnprocessedUrls();

    @Modifying
    @Transactional
    @Query("update Url u set u.processed = true where u.url = :url")
    void setProcessed(@Param("url") String url);
}
