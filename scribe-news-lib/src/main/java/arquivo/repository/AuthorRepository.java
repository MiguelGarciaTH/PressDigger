package arquivo.repository;

import arquivo.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface AuthorRepository extends JpaRepository<Author, Integer> {

    Optional<Author> findByName(String name);

    @Query(value = """
        select a
        from Author a
        where (select count(ar) from Article ar where ar.author = a) > 5
        """,
            countQuery = """
        select count(a)
        from Author a
        where (select count(ar) from Article ar where ar.author = a) > 5
        """)
    Page<Author> findAll(Pageable pageable);
}
