const axios = require("axios");
const cache = require("memory-cache");
const { GCP_ADDRESS_MAPPER_KEY_IN, GCP_BUCKET_URL, STYLI_ENV, REGION } =
  process.env;

exports.initBucket = () => {
  if (REGION === "IN") loadAdrsmpr();
};

const loadAdrsmpr = async () => {
  const url = `${GCP_BUCKET_URL}/${STYLI_ENV}/address_in.json`;
  await axios
    .get(url)
    .then((res) => {
       const data = res.data?.provinces ? res.data?.provinces.IN : {};
       cache.put(GCP_ADDRESS_MAPPER_KEY_IN, data);
    })
    .catch((err) => {
      // console.error("Error in getting adrsmpr from bucket : ", err);
    });
};
