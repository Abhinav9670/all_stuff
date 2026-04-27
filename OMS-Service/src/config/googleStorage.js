const { Storage } = require('@google-cloud/storage');
const { addAdminLog } = require('../helpers/logging');
const { insertOne } = require('../utils/mongo');
const path = require('path');
const projectId = process.env.GCP_PROJECT_ID;
console.log(`GCP Project ID : ${projectId}`);
const storage = new Storage({
  projectId
});

const uploadFileToBucket = async ({
  file,
  type,
  email,
  adminLogType,
  adminLogDesc,
  numberOfRecords
}) => {
  const tempFilePath = path.resolve(file.path);
  const { originalname: name } = file;

  let publicLink = '';
  let success = false;
  let uploadResp = {};
  const fileName = `${Date.now()}_${name}`;
  await storage
    .bucket(`${process.env.GS_BUCKET_NAME}`)
    .upload(tempFilePath, {
      // destination: `${process.env.GS_PATH_NAME}/${fileName}`,
      destination: `${type}/${fileName}`,
      metadata: {
        cacheControl: 'public, max-age=31536000'
      }
    })
    .then(async resp => {
      publicLink = resp[0]?.metadata?.mediaLink;
      success = true;
      let uploadObj = {
        email,
        fileName,
        type,
        fileUrl: publicLink,
        createdAt: new Date()
      };
      if (type === 'bankTransfer') {
        uploadObj = {
          ...uploadObj,
          status: 'pending',
          processedCount: 0,
          totalCount: numberOfRecords
        };
      }
      uploadResp = await insertOne({
        collection: 'uploads',
        data: uploadObj
      });

      await addAdminLog({
        type: adminLogType || type,
        desc: adminLogDesc || `${type} file upload`,
        data: uploadObj,
        isFileUpload: true,
        email
      });
    });
  return { success, publicLink, uploadResp };
};

const uploadFile = async (file, type) => {
  const { tempFilePath, name } = file;
  let success = false;
  const fileName = `${type}/${name}.zip`;
  await storage
    .bucket(`${process.env.GS_BUCKET_NAME}`)
    .upload(tempFilePath, {
      destination: fileName,
      metadata: {
        cacheControl: 'public, max-age=31536000'
      }
    })
    .then(async resp => {
      success = true;
    })
    .catch(err => {
      console.error('Error in uploading document to GCP Storage. ', err);
    });
  return { success, fileName };
};


const generateSignedUrl = async (filename) => {
  const expirationTime = 60 * 10; //minutes
  const bucketName = `${process.env.RETURN_INVOICE_BUCKET_URL}`
  const options = {
    action: 'read',
    expires: Date.now() + expirationTime * 1000, // Convert to milliseconds
  };
  const file = await storage.bucket(bucketName).file(filename);
  return file.getSignedUrl(options);
};

const updateHitsWithSignedUrls = async (hits) => {
  return await Promise.all(
    hits.map(async (hit) => {
      if (hit.shipping_label) {
        const filename = hit.shipping_label.split('/').pop(); 
        const [signedUrl] = await generateSignedUrl(filename); 
        return {
          ...hit,
          shipping_label: signedUrl, 
        };
      }
      return hit;
    })
  );
};

module.exports = { uploadFileToBucket, storage, uploadFile, generateSignedUrl, updateHitsWithSignedUrls };
