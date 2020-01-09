package com.topcoder.scraper.group;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.topcoder.common.dao.ProductDAO;
import com.topcoder.common.dao.ProductGroupDAO;
import com.topcoder.common.repository.ProductGroupRepository;
import com.topcoder.common.repository.ProductRepository;
import com.topcoder.scraper.module.IProductModule;
import com.topcoder.scraper.module.ecunifiedmodule.GeneralProductModule;
import com.topcoder.scraper.service.ECSiteService;

public abstract class AbstractProductGroupBuilder {

  private static final Logger logger = LoggerFactory.getLogger(AbstractProductGroupBuilder.class);

  @Autowired
  ProductGroupRepository productGroupRepository;

  @Autowired
  ProductRepository productRepository;

  @Autowired
  GeneralProductModule productModule;

  @Autowired
  ECSiteService ecSiteService;

  @Value("${scraper.matching.price_tolerance:0.1}")
  Float priceTolerance;

  abstract String getGroupingMethod();

  abstract List<ProductDAO> findSameProducts(ProductDAO prod);

  abstract String getSearchParameter(ProductDAO product);

  public List<ProductDAO> createProductGroup(ProductDAO product, Set<String> targetECSites) {

    logger.info(String.format("Atempt to group the product#%d by \"%s\" ([%s] %s)",
        product.getId(), getGroupingMethod(), product.getEcSite(), product.getProductName()));

    List<ProductDAO> sameProducts = findSameProducts(product);

    Set<String> productEcSites = getECSites(sameProducts);
    targetECSites.forEach(site -> {
      if (!productEcSites.contains(site)) {
        IProductModule productSearcher = getProjectModule(site);
        if (productSearcher == null) {
          logger.warn(String.format("'%s' is not supported.", site));
          return;
        }
        try {
          ProductDAO result = productSearcher.searchProductInfo(site, getSearchParameter(product));
          if (result == null) {
            logger.info(String.format("No product for '%s' found in %s.", product.getModelNo(), site));
            return;
          }
          if (compareProducts(product, result)) {
            result.setEcSite(site);
            sameProducts.add(result);
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }
      }
    });

    if (sameProducts.size() <= 1) {
      return sameProducts;
    }
    // create a group if there are products more than 1.
    ProductGroupDAO group = createOrUpdateGroup(getGroupingMethod(), sameProducts);
    if (group != null) {
      groupProducts(sameProducts, group);
    }
    return sameProducts;
  }

  public Set<String> getECSites(List<ProductDAO> products) {
    Set<String> ecSites = new HashSet<String>();
    if (products == null || products.size() == 0) {
      return ecSites;
    }
    products.forEach(p -> {
      if (!StringUtils.isBlank(p.getEcSite())) {
        ecSites.add(p.getEcSite());
      }
    });
    return ecSites;
  }

  public void groupProducts(List<ProductDAO> products, ProductGroupDAO group) {
    if (group == null) {
      return;
    }
    List<ProductDAO> productsNeedUpdate = new LinkedList<>();
    products.forEach(p -> {
      if (p.getProductGroupId() != null) {
        logger.warn(String.format("Conflict detected. Product:%d has already been in the group:%d.", p.getId(),
            p.getProductGroupId()));
        return;
      }
      p.setProductGroupId(group.getId());
      p.setGroupStatus(ProductDAO.GroupStatus.grouped);
      productsNeedUpdate.add(p);
    });
    this.productRepository.save(productsNeedUpdate);
  }

  public ProductGroupDAO findProductGroup(List<ProductDAO> candidateProducts) {
    if (candidateProducts == null || candidateProducts.size() == 0) {
      return null;
    }
    Integer groupId = null;
    for (Iterator<ProductDAO> iter = candidateProducts.iterator(); iter.hasNext();) {
      ProductDAO p = iter.next();
      if (p.getProductGroupId() != null) {
        groupId = p.getProductGroupId();
        break;
      }
    }
    logger.debug("Product Group: " + groupId);
    if (groupId == null) {
      return null;
    }
    return this.productGroupRepository.findOne(groupId);
  }

  public ProductGroupDAO createOrUpdateGroup(String groupingMethod, List<ProductDAO> sameProducts) {
    if (sameProducts == null) {
      return null;
    }
    ProductGroupDAO group = findProductGroup(sameProducts);
    if (group == null) {
      group = new ProductGroupDAO();
      group.setGroupingMethod(groupingMethod);
    }
    if (ProductGroupDAO.GroupingMethod.same_no.equalsIgnoreCase(groupingMethod)) {
      group.setModelNo(sameProducts.get(0).getModelNo());
    } else if (ProductGroupDAO.GroupingMethod.jan_code.equalsIgnoreCase(groupingMethod)) {
      group.setJanCode(sameProducts.get(0).getJanCode());
    } else if (ProductGroupDAO.GroupingMethod.product_name.equalsIgnoreCase(groupingMethod)) {
      group.setProductName(sameProducts.get(0).getProductName());
    }
    return this.productGroupRepository.save(group);
  }

  public IProductModule getProjectModule(String site) {
    if (site == null) {
      throw new IllegalArgumentException("site must be specified.");
    }
    Set<String> ecSiteS = ecSiteService.getAllECSites();
    if (!ecSiteS.contains(site)) {
      return null;
    }
    return this.productModule;
  }

  public boolean compareProducts(ProductDAO baseProduct, ProductDAO candidateProduct) {
    logger.debug(String.format("matching products: [%d]%s <-> [%d]%s",
        baseProduct.getId(), baseProduct.getProductName(), candidateProduct.getId(),
        candidateProduct.getProductName()));

    logger.debug(String.format("matching with model-no: [%d]%s <-> %s", baseProduct.getId(), baseProduct.getModelNo(),
        candidateProduct.getModelNo()));
    if (!StringUtils.isBlank(baseProduct.getModelNo()) && !StringUtils.isBlank(candidateProduct.getModelNo())) {
      if (baseProduct.getModelNo().equalsIgnoreCase(candidateProduct.getModelNo())) {
        logger.debug("products matched with model-no: " + baseProduct.getModelNo());
        return true;
      }
      return false;
    }

    logger.debug(String.format("matching with jan-code: [%d]%s <-> %s", baseProduct.getId(), baseProduct.getJanCode(),
        candidateProduct.getJanCode()));
    if (!StringUtils.isBlank(baseProduct.getJanCode()) && !StringUtils.isBlank(candidateProduct.getJanCode())) {
      if (baseProduct.getJanCode().equalsIgnoreCase(candidateProduct.getJanCode())) {
        logger.debug("products matched with jan-code: " + baseProduct.getJanCode());
        return true;
      }
      return false;
    }

    logger.debug(String.format("matching with unit-price: [%d]%f <-> %f", baseProduct.getId(),
        baseProduct.getUnitPriceAsNumber(), candidateProduct.getUnitPriceAsNumber()));
    logger.debug(String.format("price tolerance: %f", priceTolerance));

    Float basePrice = baseProduct.getUnitPriceAsNumber();
    Float rangeParam = basePrice * this.priceTolerance;
    if (basePrice != null) {
      Float price = candidateProduct.getUnitPriceAsNumber();
      if (basePrice - rangeParam <= price && price <= basePrice + rangeParam) {
        logger.debug(String.format("products matched with unit-price: %f <= %f <= %f [range: +-%f]",
            (basePrice - rangeParam), price, (basePrice + rangeParam), rangeParam));
        return true;
      }
    }
    return false;
  }
}