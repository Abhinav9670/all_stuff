exports.getStoreConfigs = ({ key, storeId }) => {
  const appConfig = global.config;
  let storesData = appConfig?.environments?.[0]?.stores;
  if (key) {
    storesData = storesData.map(s => {
      const storeResponse = { storeId: s.storeId };
      if (typeof key === 'object') {
        key.forEach(k => {
          storeResponse[k] = s[k];
        });
      } else {
        storeResponse[key] = s[key];
      }
      return storeResponse;
    });
  }
  if (storeId) {
    storesData = storesData.filter(s => String(s.storeId) === String(storeId));
  }
  return storesData;
};

exports.getStoreCountryMap = () => {
  const storeConfigs = this.getStoreConfigs({ key: 'websiteCode' });
  return storeConfigs.reduce((countryMap, country) => {
    countryMap[Number(country.storeId)] = country.websiteCode;
    return countryMap;
  }, {});
};

exports.getStoreWebsiteIdMap = () => {
  const storeConfigs = this.getStoreConfigs({ key: 'websiteId' });
  return storeConfigs.reduce((countryMap, country) => {
    countryMap[Number(country.storeId)] = country.websiteId;
    return countryMap;
  }, {});
};

exports.getCountryStoreMap = () => {
  const storeConfigs = this.getStoreConfigs({ key: 'websiteCode' });
  return storeConfigs.reduce((countryMap, country) => {
    if (!countryMap[country.websiteCode]) {
      countryMap[country.websiteCode] = [];
    }
    countryMap[country.websiteCode].push(country.storeId);
    return countryMap;
  }, {});
};

exports.getWebsiteStoreMap = () => {
  const storeConfigs = this.getStoreConfigs({ key: 'websiteId' });
  return storeConfigs.reduce((countryMap, country) => {
    if (!countryMap[country.websiteId]) {
      countryMap[country.websiteId] = [];
    }
    countryMap[country.websiteId].push(country.storeId);
    return countryMap;
  }, {});
};

exports.getFeatureEnabled = ({key, storeId}) => {
  const featureConfig = global?.featureConfig || {};
  return !!(featureConfig?.[key] || [] ).find(e => e == storeId);
};

exports.frontendURLBasedOnStoreId = {
  1: 'sa/en',
  3: 'sa/ar',
  7: 'ae/en',
  11: 'ae/ar',
  12: 'kw/en',
  13: 'kw/ar',
  15: 'qa/en',
  17: 'qa/ar',
  19: 'bh/en',
  21: 'bh/ar',
  23: 'om/en',
  25: 'om/ar'
};

exports.storeMap = {
  1: [1, 3],
  3: [1, 3],
  7: [7, 11],
  11: [7, 11],
  12: [12, 13],
  13: [12, 13],
  15: [17, 15],
  17: [17, 15],
  19: [19, 21],
  21: [19, 21],
  23: [23, 25],
  25: [23, 25]
};
