package arquivo.repository;

import arquivo.model.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface AnnotationRepository extends JpaRepository<Annotation, Integer> {

    Optional<Annotation> findByUserIdAndArticleId(int userId, int articleId);
}
