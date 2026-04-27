const { isFlashSaleActive, findFlashProductsPerUser } = require('../helpers/flashUtils');
const moment = require("moment");
jest.setTimeout(50000);

describe('flash_restriction_unit_tests', () => {
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe('check_flash_status', () => {
    it('flash_active_true', async () => {
      const flashConfig = {
        active: true,
        start: new Date(),
        end: new Date(),
      }
      const isActive = await isFlashSaleActive(flashConfig);
      expect(isActive).toBe(true);
    });
    it('flash_active_false', async () => {
      const endDate = moment().subtract(1, "h").toDate();
      const flashConfig = {
        active: true,
        start: new Date(),
        end: endDate,
      }
      const isActive = await isFlashSaleActive(flashConfig);
      expect(isActive).toBe(false);
    });
    it("find_flash_users_qutoe", async () => {
      const quote = {
        customerId: "3910691",
        storeId: 1,
      };
      const parentSku = "7002725901";
      const flashSaleId = "flash_sale_1";

      const result = await findFlashProductsPerUser({
        quote,
        parentSku,
        flashSaleId,
      });
      expect(result).toBe(0);
    });
  });
});
