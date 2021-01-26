package net.renfei.entity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@Slf4j
public class SiteMapXml implements Serializable {
    private static final long serialVersionUID = 1L;
    private String loc;
    private Changefreq changefreq;
    private float priority;
    private Date lastmod;

    public String getLastmod() {
        if (lastmod == null) {
            return "";
        }
        String str = "";
        try {
            // https://www.sitemaps.org/protocol.html#lastmoddef
            return new SimpleDateFormat("yyyy-MM-dd").format(lastmod);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }

    public SiteMapXml(String loc, Changefreq changefre, String priority) {
        this.setChangefreq(changefre);
        this.setLoc(loc);
        this.setPriority(Float.parseFloat(priority));
    }

    public SiteMapXml(String loc, Changefreq changefre, String priority, Date lastmod) {
        this.setChangefreq(changefre);
        this.setLoc(loc);
        this.setPriority(Float.parseFloat(priority));
        this.setLastmod(lastmod);
    }
}
