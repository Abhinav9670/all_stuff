import React, { useEffect, useState } from 'react';
import { FormGroup, Label, CustomInput, Form, Input, Row } from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import Switch from 'rc-switch';

export default function CostumForm({ actionDataeditData = {}, domain = '', editData = {}, setData = () => {} }) {
  return (
    <div className="pb-4">
      <Form>
        <FormGroup>
          <Label for="exCustomCheckbox">Name</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              onChange={e => setData({ ...editData, name: e.target.value })}
              value={editData.name || ''}
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">Domain</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={editData.domain || domain}
              onChange={e => setData({ ...editData, domain: e.target.value })}
              disabled
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">Type</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={editData.type || ''}
              onChange={e => setData({ ...editData, type: e.target.value })}
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">Description</Label>
          <div>
            <Input
              type="textarea"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={editData.description || ''}
              onChange={e => setData({ ...editData, description: e.target.value })}
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">Url</Label>
          <div>
            <Input
              type="text"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={editData.url || ''}
              onChange={e => setData({ ...editData, url: e.target.value })}
            />
          </div>
        </FormGroup>
        <FormGroup>
          <Label for="exCustomCheckbox">http Method</Label>
          <div>
            <Input
              type="select"
              id="exCustomCheckbox"
              label="Check this custom checkbox"
              value={editData.httpMethod || null}
              onChange={e => setData({ ...editData, httpMethod: e.target.value })}
            >
              <option>Choose Here</option>
              <option value="GET">Get</option>
              <option value="POST">Post</option>
              <option value="PUT">Put</option>
              <option value="PATCH">Patch</option>
              <option value="OPTIONS">Options</option>
              <option value="DELETE">Delete</option>
            </Input>
          </div>
        </FormGroup>
        <Row>
          <Colxx xxs="4">
            <FormGroup>
              <Label for="exCustomCheckbox">Authorization</Label>
              <Switch
                className="custom-switch custom-switch-primary custom-switch-small"
                checked={editData.authorization || false}
                onChange={primary => {
                  setData({ ...editData, authorization: primary });
                }}
              />
            </FormGroup>
          </Colxx>
          <Colxx xxs="4">
            <FormGroup>
              <Label for="exCustomCheckbox">Authentication</Label>
              <Switch
                className="custom-switch custom-switch-primary custom-switch-small"
                checked={editData.authentication || false}
                onChange={primary => {
                  setData({ ...editData, authentication: primary });
                }}
              />
            </FormGroup>
          </Colxx>
          <Colxx xxs="4">
            <FormGroup>
              <Label for="exCustomCheckbox">Status</Label>
              <Switch
                className="custom-switch custom-switch-primary custom-switch-small"
                checked={editData.status || false}
                onChange={primary => {
                  setData({ ...editData, status: primary });
                }}
              />
            </FormGroup>
          </Colxx>
        </Row>
      </Form>
    </div>
  );
}
