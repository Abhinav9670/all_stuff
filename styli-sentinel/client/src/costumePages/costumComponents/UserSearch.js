import React, { useState } from 'react';
import { Form, Input } from 'reactstrap';

export default function UserSearch({ handleUserSearch = () => { } }) {
  const [searchUser, setSearchUser] = useState('');

  const handleSubmit = e => {
    e.preventDefault();
    handleUserSearch(searchUser);
  };

  return (
    <div>
      <Form onSubmit={handleSubmit}>
        <Input
          placeholder="Search user"
          value={searchUser}
          onChange={e => { setSearchUser(e.target.value); handleUserSearch(e.target.value); }}
          onKeyPress={e => { }}
          style={{ borderRadius: '5px' }}
        />
      </Form>
    </div>
  );
}
