const consul = require('../../src/helpers/consul');
const axios = require('axios');

jest.mock('axios');

global.logError = jest.fn();

const OLD_ENV = process.env;

describe('consul helper', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env = { ...OLD_ENV, CONSUL_HOST: 'host', CONSUL_PORT: '8500', CONSUL_TOKEN: 'token' };
  });

  afterAll(() => {
    process.env = OLD_ENV;
  });

  describe('updateConsul', () => {
    it('should update consul and return data', async () => {
      axios.put.mockResolvedValue({ data: 'ok' });
      const result = await consul.updateConsul('key', 'value');
      expect(result).toBe('ok');
      expect(axios.put).toHaveBeenCalledWith(
        'http://host:8500/v1/kv/key',
        'value',
        { headers: { Authorization: 'Bearer token' } }
      );
    });

    it('should log error and return undefined on failure', async () => {
      axios.put.mockRejectedValue(new Error('fail'));
      const result = await consul.updateConsul('key', 'value');
      expect(result).toBeUndefined();
      expect(global.logError).toHaveBeenCalled();
    });
  });

  describe('fetchConsul', () => {
    it('should fetch consul and return data', async () => {
      axios.get.mockResolvedValue({ data: { foo: 'bar' } });
      const result = await consul.fetchConsul('key');
      expect(result).toEqual({ foo: 'bar' });
      expect(axios.get).toHaveBeenCalledWith(
        'http://host:8500/v1/kv/key?raw=true',
        { headers: { Authorization: 'Bearer token' } }
      );
    });

    it('should log error and return {} on failure', async () => {
      axios.get.mockRejectedValue(new Error('fail'));
      const result = await consul.fetchConsul('key');
      expect(result).toEqual({});
      expect(global.logError).toHaveBeenCalled();
    });
  });
});
