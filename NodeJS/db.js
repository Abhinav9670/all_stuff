import { MongoClient } from 'mongodb';

const URL = 'mongodb+srv://roshansinha:GxsxuAc0tIMnXkgy@moviecluster.nv2tv6v.mongodb.net/?retryWrites=true&w=majority&appName=MovieCluster';

const client = new MongoClient(URL, {
    useNewUrlParser: true,
    useUnifiedTopology: true
});

export const Connection = async () => {

    try {
        await client.connect();
        console.log("Database Connected Successfully");
        // You can now use `client` to interact with the database
    } catch (err) {
        console.log("Error while connecting with the database", err);
    } finally {
        await client.close();
    }
};

export const getMovieTitle = async(title)=>{
const movieTitle = title;

try {

    await client.connect();
    const database = client.db('movie_db'); // Replace 'yourDatabaseName' with the actual database name
    const collection = database.collection('video_collection');

    const movie = await collection.findOne({ movie_title: movieTitle });

    if (movie) {
        await client.close();
        return movie;
    } else {
        return {msg:"no movie found"}
    }
} catch (err) {
    console.error("Error while fetching the movie1:", err);
    res.status(500).json({ message: 'Internal Server Error' });
} 

}

export const getInfoCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('info_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
  
  
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie2:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const castCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('cast_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
  
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie3:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const getMetaDataCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('metadata_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
    
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie4:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const getSocialMediaCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('social_media_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
  
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie5:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const getAudioEventCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('audio_event_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
    
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie6:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const getVideoEventCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('video_event_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
    
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie7:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const updateMovieInfo = async (title,profanity_length) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); 
      const collection = database.collection('video_event_collection'); 
      const filter = { video_id: movieTitle };
      const options = { returnDocument: 'after' }; 

      const updateData = {
        $set: {
          'result.type.Profanity': profanity_length
        }
      };
  
      const result = await collection.findOneAndUpdate(
        filter,
        updateData,
        options
      );
    
      if (result.value) {
        await client.close(); // Close the connection after the operation
        return result.value;
      } else {
        return { msg: "no movie found to update" };
      }
    } catch (err) {
      console.error("Error while updating the movie8:", err);
      throw new Error('Internal Server Error');
    }
  };

  export const getMarketVideoCollection = async (title) => {
    const movieTitle = title;
  
    try {
      await client.connect();
      const database = client.db('movie_db'); // Replace 'movie_db' with the actual database name
      const collection = database.collection('keyclip_collection'); // Change to 'info_collection'
  
      const movie = await collection.findOne({ video_id: movieTitle });
    
      if (movie) {
        await client.close();
        return movie;
      } else {
        return { msg: "no info found" };
      }
    } catch (err) {
      console.error("Error while fetching the movie7:", err);
      throw new Error('Internal Server Error');
    }
  };

  

