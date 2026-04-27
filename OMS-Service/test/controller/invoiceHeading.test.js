const { getStoreWiseHeadings } = require('../../src/helpers/invoiceHeadings');
const expectedResponse = require("./constants/invoiceHeading.json")

describe('getStoreWiseHeadings', () => {
  it('getStoreWiseHeadings', async () => {
    const result = getStoreWiseHeadings({ storeId: '1' });
      expect(JSON.stringify(result)).toBe(JSON.stringify(expectedResponse.forStoreID_1));
  });

  it('getStoreWiseHeadings', async () => {
    const result = getStoreWiseHeadings({ storeId: '3' });
    expect(JSON.stringify(result)).toBe(JSON.stringify(expectedResponse.forStoreID_3));
  });

  it('getStoreWiseHeadings', async () => {
    const result = getStoreWiseHeadings({ storeId: '7' });
    expect(JSON.stringify(result)).toBe(JSON.stringify(expectedResponse.forStoreID_7));
  });

  it('getStoreWiseHeadings', async () => {
    const result = getStoreWiseHeadings({ storeId: '11' });
    expect(JSON.stringify(result)).toBe(JSON.stringify(expectedResponse.forStoreID_11));
  });
});
