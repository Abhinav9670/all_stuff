const {
  getCurrency,
  getPrice,
  getSpecialPrice,
  getStoreConfig,
  getBaseConfig,
  logError
} = require('../helpers/utils');
const _ = require('lodash');

exports.formatQuoteItem = ({
  elasticData,
  requestData,
  storeId,
  toUpdateQty,
  existingQuoteItem
}) => {
  const landedCost = elasticData?.original_base_price;
  const gender = elasticData?.gender || '';
  const currency = getCurrency({ storeId });

  const priceObj = elasticData?.price;
  if (!priceObj || !priceObj[currency]) return undefined;
  let price = getPrice({ priceObj: priceObj[currency] });
  let specialPrice = getSpecialPrice({ priceObj: priceObj[currency], price });
  let finalPrice = specialPrice != null ? specialPrice : price;

  const priceInclTax = elasticData?.is_gift ? 0 : finalPrice;

  const parentSku = elasticData.sku[0] || '';
  const childProductData =
    elasticData?.configProducts?.filter(
      configProd => configProd.sku === requestData.sku
    )[0] || {};
  const enrich = elasticData?.enrich || {};
  let formattedItem = {};
  if (existingQuoteItem) {
    formattedItem = {
      ...existingQuoteItem,
      qty: Math.min(Number(toUpdateQty), 10)
    };
  } else {
    formattedItem = {
      addedAt: new Date(),
      landedCost,
      parentProductId: String(elasticData.objectID),
      productId: childProductData.id || requestData.productId || '',
      storeId: storeId,
      parentSku: parentSku,
      sku: childProductData?.sku || '',
      name: elasticData.name || '',
      isGift: elasticData?.is_gift,
      qty: Math.min(Number(toUpdateQty), 10),
      price: finalPrice,
      basePrice: finalPrice,
      originalPrice: price,
      discountPercent: elasticData.discount_percentage || 0,
      soldBy: elasticData.sold_by,
      brandName: elasticData.brand_name,
      qtySold: elasticData.last7qty ? parseInt(elasticData.last7qty) : 0,
      imgUrl: elasticData.image_url,
      discountAmount: 0,
      baseDiscountAmount: 0,
      taxPercent: 0,
      taxAmount: 0,
      baseTaxAmount: 0,
      rowTotal: 0,
      baseRowTotal: 0,
      rowTotalWithDiscount: 0,
      productType: 'simple',
      priceInclTax: priceInclTax,
      basePriceInclTax: priceInclTax,
      rowTotalInclTax: 0,
      baseRowTotalInclTax: 0,
      discountTaxCompensationAmount: 0,
      baseDiscountTaxCompensationAmount: 0,
      superAttributeId: childProductData.superAttributeId || '',
      superAttributeValue: childProductData.sizeOptionId || '',
      superAttributeLabel: childProductData.size || '',
      caSource: requestData.caSource,
      caSourceType: requestData.caSourceType,
      caSourceValue: requestData.caSourceValue,
      caBannerPromoName: requestData.caBannerPromoName,
      caSearchId: requestData.caSearchId,
      gender,
      isDangerousProduct: Boolean(elasticData?.is_dangerous_product === 1 || elasticData?.is_dangerous_product === true || elasticData?.productAttributes?.is_dangerous_product === 1 || elasticData?.productAttributes?.is_dangerous_product === true),
      shortDescription: elasticData?.short_description || elasticData?.productAttributes?.short_description || '',
      variantSku: elasticData?.variant_sku || elasticData?.productAttributes?.variant_sku || '',
    };
  }

  return formattedItem;
};

exports.getMetaData = ({ elasticData }) => {
  let categoryPaths = [];
  let categoryNames = [];
  const categoryIds = elasticData?.categoryIds || [];

  categoryNames.push(elasticData?.categories?.level0?.[0]);
  categoryPaths.push(elasticData?.categories?.level0?.[0]);

  const categoryPathsL2 = elasticData?.categories?.level1 || [];
  const categoryNamesL2 = getCategoryNames({
    categoryPathArr: categoryPathsL2
  });
  categoryNames = [...categoryNames, ...categoryNamesL2];

  const categoryPathsL3 = elasticData?.categories?.level2 || [];
  const categoryNamesL3 = getCategoryNames({
    categoryPathArr: categoryPathsL3
  });
  categoryNames = [...categoryNames, ...categoryNamesL3];

  const categoryPathsL4 = elasticData?.categories?.level3 || [];
  const categoryNamesL4 = getCategoryNames({
    categoryPathArr: categoryPathsL4
  });
  categoryNames = [...categoryNames, ...categoryNamesL4];

  categoryPaths = [
    ...categoryPaths,
    ...categoryPathsL2,
    ...categoryPathsL3,
    ...categoryPathsL4
  ];

  const level1 = [
    {
      categoryId: categoryIds[0],
      categoryName: categoryNames[0],
      path: categoryPaths[0]
    }
  ];

  const level2 = categoryPathsL2.map(path => {
    const pathIndex = categoryPaths.indexOf(path);
    return {
      categoryId: categoryIds[pathIndex],
      categoryName: categoryNames[pathIndex],
      path
    };
  });

  const level3 = categoryPathsL3.map(path => {
    const pathIndex = categoryPaths.indexOf(path);
    return {
      categoryId: categoryIds[pathIndex],
      categoryName: categoryNames[pathIndex],
      path
    };
  });

  const level4 = categoryPathsL4.map(path => {
    const pathIndex = categoryPaths.indexOf(path);
    return {
      categoryId: categoryIds[pathIndex],
      categoryName: categoryNames[pathIndex],
      path
    };
  });

  const metaData = {
    'level-1': level1,
    'level-2': level2,
    'level-3': level3,
    'level-4': level4,
    sku: elasticData?.sku[0],
    productId: elasticData?.objectID
  };

  return metaData;
};

const getCategoryNames = ({ categoryPathArr }) => {
  const categoryNameArray = categoryPathArr?.map(categoryPath => {
    if (!categoryPath) return '';
    return categoryPath.split('///').slice(-1)[0]?.trim() || '';
  });

  return categoryNameArray;
};

exports.getContextId = ({ enrich, storeId, xHeaderToken = '' }) => {
  let contextId = '';
  try {
    if(!_.isEmpty(enrich)){
      const websiteCode = getStoreConfig(storeId, 'websiteCode');
      const giftConfig = getBaseConfig('giftProducts');
      const countryConfig = giftConfig?.[websiteCode] || {};
      const { collections } = countryConfig || {};
      collections?.forEach(collection => {
        if (enrich?.[`c_${collection?.contextId}`] === 1) {
          contextId = collection?.contextId;
        }
      });
    }
  } catch (e) {
    logError(e, 'Error fetching contextId', xHeaderToken);
  }
  return contextId;
};

exports.checkIsGwp = ({ enrich, storeId, xHeaderToken = '' }) => {
  let isGwp = false;
  try {
    if(!_.isEmpty(enrich)){
      const websiteCode = getStoreConfig(storeId, 'websiteCode');
      const giftConfig = getBaseConfig('giftProducts');
      const countryConfig = giftConfig?.[websiteCode] || {};
      const { enable_gift_with_purchase_module_feature, eligible_products_context_id } = countryConfig || {};
      
      if (enable_gift_with_purchase_module_feature && eligible_products_context_id && enrich?.[`c_${eligible_products_context_id}`] === 1) {
        isGwp = true;
      }
    }
  } catch (e) {
    logError(e, 'Error checking isGwp', xHeaderToken);
  }
  return isGwp;
};

exports.sortProducts = (products = []) => {
  const normalProducts = [];
  const giftProducts = [];
  products.forEach(product => {
    const { isGift } = product;
    if (isGift) {
      giftProducts.push(product);
    } else {
      normalProducts.push(product);
    }
  });

  return [...normalProducts, ...giftProducts];
};
