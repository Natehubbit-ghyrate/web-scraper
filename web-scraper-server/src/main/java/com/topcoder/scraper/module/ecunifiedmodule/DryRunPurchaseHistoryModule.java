package com.topcoder.scraper.module.ecunifiedmodule;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.topcoder.common.dao.ECSiteAccountDAO;
import com.topcoder.common.model.PurchaseHistory;
import com.topcoder.common.repository.ECSiteAccountRepository;
import com.topcoder.common.repository.ScraperRepository;
import com.topcoder.common.traffic.TrafficWebClient;
import com.topcoder.common.traffic.TrafficWebClient.TrafficWebClientForDryRun;
import com.topcoder.common.util.Common;
import com.topcoder.scraper.module.IPurchaseHistoryModule;
import com.topcoder.scraper.module.ecunifiedmodule.crawler.GeneralPurchaseHistoryCrawler;
import com.topcoder.scraper.module.ecunifiedmodule.crawler.GeneralPurchaseHistoryCrawlerResult;
import com.topcoder.scraper.service.PurchaseHistoryService;
import com.topcoder.scraper.service.WebpageService;

/**
 * General implementation of ecisolatedmodule .. PurchaseHistoryModule
 */
// TODO: refactoring to imitate AbstractPurchaseHistoryModule
@Component
public class DryRunPurchaseHistoryModule implements IPurchaseHistoryModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(DryRunPurchaseHistoryModule.class);

  private final PurchaseHistoryService purchaseHistoryService;
  private final WebpageService webpageService;
  private final ECSiteAccountRepository ecSiteAccountRepository;

  private String script;
  private List<PurchaseHistory> list;

  @Autowired
  ScraperRepository scraperRepository;

  // TODO: arrange login handler
  //private final LoginHandlerBase loginHandler;

  @Autowired
  public DryRunPurchaseHistoryModule(PurchaseHistoryService purchaseHistoryService, ECSiteAccountRepository ecSiteAccountRepository, WebpageService webpageService
  //LoginHandlerBase loginHandler
  ) {
    this.purchaseHistoryService = purchaseHistoryService;
    this.webpageService = webpageService;
    this.ecSiteAccountRepository = ecSiteAccountRepository;
    // TODO: arrange login handler
    //this.loginHandler = loginHandler;
  }

  public void setScript(String script) {
	this.script = script;
  }

  @Override
  public String getModuleType() {
    return "dryrun";
  }

  @Override
  public void fetchPurchaseHistoryList(List<String> sites) throws IOException {

    // reset list
    list = null;
    ECSiteAccountDAO accountDAO = null;
    String site = sites.get(0);

    Iterable<ECSiteAccountDAO> accountDAOS = ecSiteAccountRepository.findAllByEcSite(site);
    for (ECSiteAccountDAO ecSiteAccountDAO : accountDAOS) {
      if (ecSiteAccountDAO.getEcUseFlag() == Boolean.TRUE) {
        accountDAO = ecSiteAccountDAO;
        break;
      }
    }

    if (accountDAO == null) {
      LOGGER.error("failed to get ecSite account");
      return;
    }

    TrafficWebClient webClient = new TrafficWebClient(accountDAO.getUserId(), true);
    TrafficWebClientForDryRun webClientForDryRun = webClient.new TrafficWebClientForDryRun(accountDAO.getUserId(), true);
    LOGGER.info("web client version = " + webClient.getWebClient().getBrowserVersion());
    boolean restoreRet = Common.restoreCookies(webClientForDryRun.getWebClient(), accountDAO);
    if (!restoreRet) {
      LOGGER.error("skip ecSite id = " + accountDAO.getId() + ", restore cookies failed");
      return;
    }

    try {
      GeneralPurchaseHistoryCrawler crawler = new GeneralPurchaseHistoryCrawler(site, this.webpageService, this.scraperRepository);
      crawler.setScript(this.script);
      GeneralPurchaseHistoryCrawlerResult crawlerResult = crawler.fetchPurchaseHistoryList(webClientForDryRun, null, false);
      this.list = crawlerResult.getPurchaseHistoryList();
      LOGGER.info("succeed fetch purchaseHistory for ecSite id = " + accountDAO.getId());
    } catch (Exception e) {
      LOGGER.error("failed to PurchaseHistory for ecSite id = " + accountDAO.getId());
      e.printStackTrace();
    }

  }

  public List<PurchaseHistory> getPurchaseHistoryList() {
	  return this.list;
  }

}