/*******************************************************************************
 * Copyright (c) 2011, 2020 Eurotech and/or its affiliates and others
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *  Eurotech
 *******************************************************************************/
package org.eclipse.kura.web.client.ui.security;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.Tab;
import org.eclipse.kura.web.client.util.request.RequestQueue;
import org.eclipse.kura.web.shared.service.GwtCertificatesService;
import org.eclipse.kura.web.shared.service.GwtCertificatesServiceAsync;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenService;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenServiceAsync;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Form;
import org.gwtbootstrap3.client.ui.FormGroup;
import org.gwtbootstrap3.client.ui.FormLabel;
import org.gwtbootstrap3.client.ui.Input;
import org.gwtbootstrap3.client.ui.TextArea;
import org.gwtbootstrap3.client.ui.form.error.BasicEditorError;
import org.gwtbootstrap3.client.ui.form.validator.ValidationChangedEvent.ValidationChangedHandler;
import org.gwtbootstrap3.client.ui.form.validator.Validator;
import org.gwtbootstrap3.client.ui.html.Span;

import com.google.gwt.core.client.GWT;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorError;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

public class ServerCertsTabUi extends Composite implements Tab {

    private static ServerCertsTabUiUiBinder uiBinder = GWT.create(ServerCertsTabUiUiBinder.class);

    interface ServerCertsTabUiUiBinder extends UiBinder<Widget, ServerCertsTabUi> {
    }

    private static final Messages MSGS = GWT.create(Messages.class);

    private final GwtSecurityTokenServiceAsync gwtXSRFService = GWT.create(GwtSecurityTokenService.class);
    private final GwtCertificatesServiceAsync gwtCertificatesService = GWT.create(GwtCertificatesService.class);

    private final CertificateModalListener listener;

    private boolean dirty;

    @UiField
    HTMLPanel description;
    @UiField
    Form serverSslCertsForm;
    @UiField
    FormGroup groupStorageAliasForm;
    @UiField
    FormGroup groupCertForm;
    @UiField
    FormLabel storageAliasLabel;
    @UiField
    FormLabel certificateLabel;
    @UiField
    Input storageAliasInput;
    @UiField
    TextArea certificateInput;
    @UiField
    Button reset;
    @UiField
    Button apply;

    public ServerCertsTabUi(final CertificateModalListener listener) {
        this.listener = listener;
        initWidget(uiBinder.createAndBindUi(this));
        initForm();

        setDirty(false);
        this.apply.setEnabled(false);
        this.reset.setEnabled(false);
    }

    @Override
    public void setDirty(boolean flag) {
        this.dirty = flag;
        this.reset.setEnabled(flag);
        this.apply.setEnabled(isValid());
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public boolean isValid() {
        boolean validAlias = this.storageAliasInput.validate();
        boolean validAppCert = this.certificateInput.validate();
        boolean result = false;
        if (validAlias && validAppCert) {
            result = true;
        }
        return result;
    }

    @Override
    public void refresh() {
        if (isDirty()) {
            setDirty(false);
            reset();
        }
    }

    private void initForm() {
        StringBuilder title = new StringBuilder();
        title.append("<p>");
        title.append(MSGS.settingsAddCertDescription1());
        title.append(" ");
        title.append(MSGS.settingsAddCertDescription2());
        title.append("</p>");
        this.description.add(new Span(title.toString()));

        final Validator<String> validator = new Validator<String>() {

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public List<EditorError> validate(Editor<String> editor, String value) {
                final List<EditorError> result = new ArrayList<>();

                if (value == null || value.isEmpty()) {
                    result.add(new BasicEditorError(editor, value, MSGS.formRequiredParameter()));
                }

                return result;
            }
        };

        final ValidationChangedHandler validationChangeHandler = e -> this.apply.setEnabled(isValid());

        this.storageAliasInput.addValidationChangedHandler(validationChangeHandler);
        this.certificateInput.addValidationChangedHandler(validationChangeHandler);

        this.storageAliasInput.addValidator(validator);
        this.certificateInput.addValidator(validator);

        this.storageAliasInput.addKeyUpHandler(e -> {
            this.storageAliasInput.validate();
            setDirty(true);
        });
        this.certificateInput.addKeyUpHandler(e -> {
            this.certificateInput.validate();
            setDirty(true);
        });

        this.storageAliasLabel.setText(MSGS.settingsStorageAliasLabel());
        this.storageAliasInput.addChangeHandler(event -> {
            this.storageAliasInput.validate();
            setDirty(true);
            ServerCertsTabUi.this.apply.setEnabled(true);
            ServerCertsTabUi.this.reset.setEnabled(true);
        });

        this.certificateLabel.setText(MSGS.settingsPublicCertLabel());
        this.certificateInput.setVisibleLines(20);
        this.certificateInput.addChangeHandler(event -> {
            this.certificateInput.validate();
            setDirty(true);
        });

        this.reset.setText(MSGS.reset());
        this.reset.addClickHandler(event -> {
            reset();
            setDirty(false);
        });

        this.apply.setText(MSGS.apply());
        this.apply.addClickHandler(event -> {
            if (isValid()) {
                this.listener.onApply();
                RequestQueue.submit(c -> this.gwtXSRFService.generateSecurityToken(
                        c.callback(token -> this.gwtCertificatesService.storeSSLPublicChain(token,
                                ServerCertsTabUi.this.certificateInput.getValue(),
                                ServerCertsTabUi.this.storageAliasInput.getValue(), c.callback(ok -> {
                                    reset();
                                    setDirty(false);
                                    listener.onKeystoreChanged();
                                })))));
            }
        });

        this.storageAliasInput.validate();
        this.certificateInput.validate();
    }

    private void reset() {
        this.storageAliasInput.setText("");
        this.certificateInput.setText("");
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub

    }
}
