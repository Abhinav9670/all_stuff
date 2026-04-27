const slugify = require('slugify');

const getProductSlug = ({ name, sku }) => {
  const productNameSlug = slugify(name?.toLowerCase() || '');
  return `product-${productNameSlug}-${sku}`;
};

module.exports = getProductSlug;
