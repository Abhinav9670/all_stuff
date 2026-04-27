import React, { useState } from 'react';
import { Form, Input } from 'reactstrap';

export default function HistorySearch({ handleUserSearch = () => {} }) {
  const [searchUser, setSearchUser] = useState('');

  const handleSubmit = e => {
    handleUserSearch(searchUser);
    e.preventDefault();
  };

  return (
    <div>
      <Form onSubmit={handleSubmit}>
        <Input
          placeholder="Search history"
          value={searchUser}
          onChange={e => setSearchUser(e.target.value)}
          onKeyPress={e => {}}
          style={{ borderRadius: '5px' }}
        />
      </Form>
    </div>
  );
}
