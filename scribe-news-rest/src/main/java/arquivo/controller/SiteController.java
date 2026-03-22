package arquivo.controller;

import arquivo.model.Site;
import arquivo.repository.SiteRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sites")
public class SiteController {

    private final SiteRepository siteRepository;

    public SiteController(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @GetMapping()
    public List<Site> getSites() {
        return siteRepository.findAll();
    }

    @GetMapping("/count")
    public long countSites() {
        return siteRepository.count();
    }

}
