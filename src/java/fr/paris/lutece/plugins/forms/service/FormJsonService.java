/*
 * Copyright (c) 2002-2021, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.forms.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import fr.paris.lutece.plugins.forms.business.CompositeDisplayType;
import fr.paris.lutece.plugins.forms.business.Control;
import fr.paris.lutece.plugins.forms.business.ControlHome;
import fr.paris.lutece.plugins.forms.business.ControlMapping;
import fr.paris.lutece.plugins.forms.business.ControlType;
import fr.paris.lutece.plugins.forms.business.Form;
import fr.paris.lutece.plugins.forms.business.FormDisplay;
import fr.paris.lutece.plugins.forms.business.FormDisplayHome;
import fr.paris.lutece.plugins.forms.business.FormHome;
import fr.paris.lutece.plugins.forms.business.FormMessage;
import fr.paris.lutece.plugins.forms.business.FormMessageHome;
import fr.paris.lutece.plugins.forms.business.Group;
import fr.paris.lutece.plugins.forms.business.GroupHome;
import fr.paris.lutece.plugins.forms.business.Question;
import fr.paris.lutece.plugins.forms.business.QuestionHome;
import fr.paris.lutece.plugins.forms.business.Step;
import fr.paris.lutece.plugins.forms.business.StepHome;
import fr.paris.lutece.plugins.forms.business.Transition;
import fr.paris.lutece.plugins.forms.business.TransitionHome;
import fr.paris.lutece.plugins.forms.business.export.FormExportConfig;
import fr.paris.lutece.plugins.forms.business.export.FormExportConfigHome;
import fr.paris.lutece.plugins.forms.util.FormJsonData;
import fr.paris.lutece.plugins.forms.util.FormsConstants;
import fr.paris.lutece.plugins.forms.util.StepJsonData;
import fr.paris.lutece.plugins.forms.util.TimestampDeserializer;
import fr.paris.lutece.plugins.forms.util.TimestampSerializer;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.Field;
import fr.paris.lutece.plugins.genericattributes.business.FieldHome;
import fr.paris.lutece.plugins.genericattributes.business.ReferenceItemFieldHome;
import fr.paris.lutece.plugins.referencelist.business.ReferenceItemHome;
import fr.paris.lutece.portal.service.i18n.I18nService;

/**
 * Json service to handle import/export
 */
public class FormJsonService
{
    private static final String PROPERTY_COPY_FORM_TITLE = "forms.copyForm.title";
    private static final String PROPERTY_COPY_STEP_TITLE = "forms.copyStep.title";

    public static final FormJsonService INSTANCE = new FormJsonService( );

    private ObjectMapper _objectMapper;

    private FormJsonService( )
    {
        SimpleModule timestampModule = new SimpleModule( "TimestampModule" );
        timestampModule.addSerializer( Timestamp.class, new TimestampSerializer( ) );
        timestampModule.addDeserializer( Timestamp.class, new TimestampDeserializer( ) );

        _objectMapper = new ObjectMapper( ).configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false ).registerModule( timestampModule );
    }

    public static FormJsonService getInstance( )
    {
        return INSTANCE;
    }

    /**
     * Export the form as a Json Object.
     * 
     * @return
     * @throws JsonProcessingException
     */
    public String jsonExportForm( int idForm ) throws JsonProcessingException
    {
        FormJsonData jsonData = new FormJsonData( );
        jsonData.setForm( FormHome.findByPrimaryKey( idForm ) );
        jsonData.getForm( ).setLogo( null );

        List<Step> stepList = StepHome.getStepsListByForm( idForm );
        jsonData.setStepList( stepList );
        jsonData.setGroupList( GroupHome.getGroupsListByIdStepList( stepList.stream( ).map( Step::getId ).collect( Collectors.toList( ) ) ) );

        List<Control> controlList = new ArrayList<>( );
        List<Question> questionList = QuestionHome.getListQuestionByIdForm( idForm );
        for ( Question question : questionList )
        {
            for ( Field field : question.getEntry( ).getFields( ) )
            {
                Integer idItem = ReferenceItemFieldHome.findIdItemByIdField( field.getIdField( ) );
                if ( idItem > 0 )
                {
                    field.setLinkedItem( ReferenceItemHome.findByPrimaryKey( idItem ) );
                }
            }
            controlList.addAll( ControlHome.getControlByQuestion( question.getId( ) ) );
        }
        List<ControlMapping> controlMappingList = new ArrayList<>( );
        for ( Control control : controlList )
        {
            controlMappingList.addAll( ControlHome.getControlMappingListByIdControl( control.getId( ) ) );
        }
        jsonData.setQuestionList( questionList );
        jsonData.setControlList( controlList );

        jsonData.setControlMappingList( controlMappingList );

        jsonData.setTransitionList( TransitionHome.getTransitionsListFromForm( idForm ) );
        jsonData.setFormMessage( FormMessageHome.findByForm( idForm ) );
        jsonData.setFormDisplayList( FormDisplayHome.getFormDisplayByForm( idForm ) );

        jsonData.setFormExportConfigList( FormExportConfigHome.findByForm( idForm ) );

        return _objectMapper.writeValueAsString( jsonData );
    }

    /**
     * Import the form from a Json Object.
     * 
     * @return
     */
    @Transactional( FormsConstants.BEAN_TRANSACTION_MANAGER )
    public void jsonImportForm( String json, Locale locale ) throws JsonProcessingException
    {
        FormJsonData jsonData = _objectMapper.readValue( json, FormJsonData.class );

        int newIdForm = importForm( jsonData.getForm( ), jsonData.getFormMessage( ), locale );

        List<FormExportConfig> formExportConfigList = jsonData.getFormExportConfigList( );
        List<Step> stepList = jsonData.getStepList( );
        List<Group> groupList = jsonData.getGroupList( );
        List<Transition> transitionList = jsonData.getTransitionList( );
        List<Question> questionList = jsonData.getQuestionList( );
        List<FormDisplay> formDisplayList = jsonData.getFormDisplayList( );
        List<Control> controlList = jsonData.getControlList( );
        List<ControlMapping> controlMappingList = jsonData.getControlMappingList( );

        importSteps( newIdForm, stepList, groupList, transitionList, questionList, formDisplayList );
        importQuestions( newIdForm, questionList, controlList, controlMappingList, formDisplayList, formExportConfigList );
        importGroups( groupList, formDisplayList );
        importFormDisplay( newIdForm, formDisplayList, controlList );
        importTransitions( transitionList, controlList );
        importControls( controlList, controlMappingList );
        importFormExportConfig( newIdForm, formExportConfigList );
    }

    /**
     * Export the step as a Json Object.
     * 
     * @return
     * @throws JsonProcessingException
     */
    public String jsonExportStep( int idForm, int idStep ) throws JsonProcessingException
    {
        StepJsonData jsonData = new StepJsonData( );

        jsonData.setStep( StepHome.findByPrimaryKey( idStep ) );
        jsonData.setGroupList( GroupHome.getGroupsListByIdStepList( Collections.singletonList( idStep ) ) );

        List<Control> controlList = new ArrayList<>( );
        List<Question> questionList = QuestionHome.getQuestionsListByStep( idStep );
        for ( Question question : questionList )
        {
            for ( Field field : question.getEntry( ).getFields( ) )
            {
                Integer idItem = ReferenceItemFieldHome.findIdItemByIdField( field.getIdField( ) );
                if ( idItem > 0 )
                {
                    field.setLinkedItem( ReferenceItemHome.findByPrimaryKey( idItem ) );
                }
            }
            controlList.addAll( ControlHome.getControlByQuestion( question.getId( ) ) );
        }
        List<ControlMapping> controlMappingList = new ArrayList<>( );
        for ( Control control : controlList )
        {
            controlMappingList.addAll( ControlHome.getControlMappingListByIdControl( control.getId( ) ) );
        }
        jsonData.setQuestionList( questionList );
        jsonData.setControlList( controlList );
        jsonData.setControlMappingList( controlMappingList );

        List<FormDisplay> formDisplayList = FormDisplayHome.getFormDisplayByForm( idForm );
        jsonData.setFormDisplayList( formDisplayList.stream( ).filter( fd -> fd.getStepId( ) == idStep ).collect( Collectors.toList( ) ) );

        return _objectMapper.writeValueAsString( jsonData );
    }

    /**
     * Import the step from a Json Object.
     * 
     * @return
     */
    @Transactional( FormsConstants.BEAN_TRANSACTION_MANAGER )
    public void jsonImportStep( int idForm, String json, Locale locale ) throws JsonProcessingException
    {
        StepJsonData jsonData = _objectMapper.readValue( json, StepJsonData.class );

        Step step = jsonData.getStep( );
        List<Group> groupList = jsonData.getGroupList( );
        List<Question> questionList = jsonData.getQuestionList( );
        List<Control> controlList = jsonData.getControlList( );
        List<ControlMapping> controlMappingList = jsonData.getControlMappingList( );
        List<FormDisplay> formDisplayList = jsonData.getFormDisplayList( );

        Object [ ] tabSTepTitleCopy = {
                step.getTitle( ),
        };
        String strTitleCopyStep = I18nService.getLocalizedString( PROPERTY_COPY_STEP_TITLE, tabSTepTitleCopy, locale );

        if ( strTitleCopyStep != null )
        {
            step.setTitle( strTitleCopyStep );
        }

        importSteps( idForm, Collections.singletonList( step ), groupList, null, questionList, formDisplayList );
        importQuestions( idForm, questionList, controlList, controlMappingList, formDisplayList, null );
        importGroups( groupList, formDisplayList );
        importFormDisplay( idForm, formDisplayList, controlList );
        importControls( controlList, controlMappingList );
    }

    private int importForm( Form form, FormMessage formMessage, Locale locale )
    {
        Object [ ] tabFormTitleCopy = {
                form.getTitle( ),
        };
        String strTitleCopyForm = I18nService.getLocalizedString( PROPERTY_COPY_FORM_TITLE, tabFormTitleCopy, locale );
        if ( strTitleCopyForm != null )
        {
            form.setTitle( strTitleCopyForm );
        }
        form.setIdWorkflow( 0 );
        form.setAvailabilityStartDate( null );
        form.setAvailabilityEndDate( null );
        form.setLogo( null );
        FormHome.create( form );

        int newIdForm = form.getId( );

        formMessage.setIdForm( newIdForm );
        FormMessageHome.create( formMessage );

        return newIdForm;
    }

    private void importFormExportConfig( int newIdForm, List<FormExportConfig> formExportConfigList )
    {
        for ( FormExportConfig config : formExportConfigList )
        {
            config.setIdForm( newIdForm );
            FormExportConfigHome.create( config );
        }
    }

    private void importControls( List<Control> controlList, List<ControlMapping> controlMappingList )
    {
        Map<Integer, Integer> mapIdControls = new HashMap<>( );
        for ( Control control : controlList )
        {
            int oldId = control.getId( );
            ControlHome.create( control );
            int newId = control.getId( );

            mapIdControls.put( oldId, newId );
        }

        for ( ControlMapping controlMapping : controlMappingList )
        {
            ControlHome.createMappingControl( mapIdControls.get( controlMapping.getIdControl( ) ), controlMapping.getIdQuestion( ),
                    controlMapping.getValue( ) );
        }
    }

    private void importTransitions( List<Transition> transitionList, List<Control> controlList )
    {
        Map<Integer, Integer> mapIdTransitions = new HashMap<>( );
        for ( Transition transition : transitionList )
        {
            int oldId = transition.getId( );
            TransitionHome.createWithoutPriorityCalculation( transition );

            int newId = transition.getId( );

            mapIdTransitions.put( oldId, newId );
        }
        updateControlWithNewTransition( controlList, mapIdTransitions );
    }

    private void updateControlWithNewTransition( List<Control> controlList, Map<Integer, Integer> mapIdTransitions )
    {
        for ( Control control : controlList )
        {
            if ( ControlType.TRANSITION.getLabel( ).equals( control.getControlType( ) ) )
            {
                control.setIdControlTarget( mapIdTransitions.get( control.getIdControlTarget( ) ) );
            }
        }
    }

    private void importFormDisplay( int newIdForm, List<FormDisplay> formDisplayList, List<Control> controlList )
    {
        Map<Integer, Integer> mapIdFormDisplay = new HashMap<>( );
        for ( FormDisplay formDisplay : formDisplayList )
        {
            int oldId = formDisplay.getId( );
            formDisplay.setFormId( newIdForm );
            FormDisplayHome.create( formDisplay );

            int newId = formDisplay.getId( );

            mapIdFormDisplay.put( oldId, newId );
        }
        for ( FormDisplay formDisplay : formDisplayList )
        {
            if ( formDisplay.getParentId( ) > 0 )
            {
                formDisplay.setParentId( mapIdFormDisplay.get( formDisplay.getParentId( ) ) );
            }
            // Update id parent and display order
            FormDisplayHome.update( formDisplay );
        }
        updateControlWithFormDisplay( controlList, mapIdFormDisplay );
    }

    private void updateControlWithFormDisplay( List<Control> controlList, Map<Integer, Integer> mapIdFormDisplay )
    {
        for ( Control control : controlList )
        {
            if ( ControlType.CONDITIONAL.getLabel( ).equals( control.getControlType( ) ) && mapIdFormDisplay.containsKey( control.getIdControlTarget( ) ) )
            {
                control.setIdControlTarget( mapIdFormDisplay.get( control.getIdControlTarget( ) ) );
            }
        }
    }

    private void importGroups( List<Group> groupList, List<FormDisplay> formDisplayList )
    {
        Map<Integer, Integer> mapIdGroups = new HashMap<>( );
        for ( Group group : groupList )
        {
            int oldId = group.getId( );

            GroupHome.create( group );

            int newId = group.getId( );

            mapIdGroups.put( oldId, newId );
        }
        updateFormDisplayWithNewGroup( formDisplayList, mapIdGroups );
    }

    private void updateFormDisplayWithNewGroup( List<FormDisplay> formDisplayList, Map<Integer, Integer> mapIdGroup )
    {
        for ( FormDisplay formDisplay : formDisplayList )
        {
            if ( CompositeDisplayType.GROUP.getLabel( ).equals( formDisplay.getCompositeType( ) ) )
            {
                formDisplay.setCompositeId( mapIdGroup.get( formDisplay.getCompositeId( ) ) );
            }
        }
    }

    private void importQuestions( int newIdForm, List<Question> questionList, List<Control> controlList, List<ControlMapping> controlMappingList,
            List<FormDisplay> formDisplayList, List<FormExportConfig> formExportConfigList )
    {
        Map<Integer, Integer> mapIdQuestions = new HashMap<>( );
        for ( Question question : questionList )
        {
            int oldIdQuestion = question.getId( );

            Entry entry = question.getEntry( );
            entry.setIdResource( newIdForm );
            EntryHome.create( entry );
            question.setIdEntry( entry.getIdEntry( ) );

            List<Field> fieldList = entry.getFields( );
            for ( Field field : fieldList )
            {
                field.setParentEntry( entry );
                FieldHome.create( field );
            }

            QuestionHome.create( question );

            int newIdQuestion = question.getId( );

            mapIdQuestions.put( oldIdQuestion, newIdQuestion );
        }
        updateControlWithNewQuestion( controlMappingList, controlList, mapIdQuestions );
        updateFormDisplayWithNewQuestion( formDisplayList, mapIdQuestions );
        if ( CollectionUtils.isNotEmpty( formExportConfigList ) )
        {
            updateExportConfigWithNewQuestion( formExportConfigList, mapIdQuestions );
        }
    }

    private void updateExportConfigWithNewQuestion( List<FormExportConfig> formExportConfigList, Map<Integer, Integer> mapIdQuestions )
    {
        for ( FormExportConfig config : formExportConfigList )
        {
            int idQuestion = NumberUtils.toInt( config.getField( ), -1 );
            if ( idQuestion != -1 )
            {
                config.setField( String.valueOf( mapIdQuestions.get( idQuestion ) ) );
            }
        }
    }

    private void updateControlWithNewQuestion( List<ControlMapping> controlMappingList, List<Control> controlList, Map<Integer, Integer> mapIdQuestions )
    {
        for ( Control control : controlList )
        {
            Set<Integer> newSetId = new HashSet<>( );
            Set<Integer> oldSetId = control.getListIdQuestion( );

            for ( Integer oldId : oldSetId )
            {
                newSetId.add( mapIdQuestions.get( oldId ) );
            }
            control.setListIdQuestion( newSetId );

            if ( ControlType.VALIDATION.getLabel( ).equals( control.getControlType( ) ) )
            {
                control.setIdControlTarget( mapIdQuestions.get( control.getIdControlTarget( ) ) );
            }
        }
        for ( ControlMapping controlMapping : controlMappingList )
        {
            controlMapping.setIdQuestion( mapIdQuestions.get( controlMapping.getIdQuestion( ) ) );
        }
    }

    private void updateFormDisplayWithNewQuestion( List<FormDisplay> formDisplayList, Map<Integer, Integer> mapIdQuestions )
    {
        for ( FormDisplay formDisplay : formDisplayList )
        {
            if ( CompositeDisplayType.QUESTION.getLabel( ).equals( formDisplay.getCompositeType( ) ) )
            {
                formDisplay.setCompositeId( mapIdQuestions.get( formDisplay.getCompositeId( ) ) );
            }
        }
    }

    private void importSteps( int newIdForm, List<Step> stepList, List<Group> groupList, List<Transition> transitionList, List<Question> questionList,
            List<FormDisplay> formDisplayList )
    {
        Map<Integer, Integer> mapIdSteps = new HashMap<>( );
        for ( Step step : stepList )
        {
            int oldStepId = step.getId( );
            step.setIdForm( newIdForm );

            StepHome.create( step );
            int newStepId = step.getId( );

            mapIdSteps.put( oldStepId, newStepId );
        }

        if ( CollectionUtils.isNotEmpty( transitionList ) )
        {
            updateTransitionWithNewStep( transitionList, mapIdSteps );
        }
        updateGroupWithNewStep( groupList, mapIdSteps );
        updateQuestionWithNewStep( questionList, mapIdSteps );
        updateFormDisplayWithNewStep( formDisplayList, mapIdSteps );
    }

    private void updateTransitionWithNewStep( List<Transition> transitionList, Map<Integer, Integer> mapIdSteps )
    {
        for ( Transition transition : transitionList )
        {
            transition.setFromStep( mapIdSteps.get( transition.getFromStep( ) ) );
            transition.setNextStep( mapIdSteps.get( transition.getNextStep( ) ) );
        }
    }

    private void updateGroupWithNewStep( List<Group> groupList, Map<Integer, Integer> mapIdSteps )
    {
        for ( Group group : groupList )
        {
            group.setIdStep( mapIdSteps.get( group.getIdStep( ) ) );
        }
    }

    private void updateQuestionWithNewStep( List<Question> questionList, Map<Integer, Integer> mapIdSteps )
    {
        for ( Question question : questionList )
        {
            question.setIdStep( mapIdSteps.get( question.getIdStep( ) ) );
        }
    }

    private void updateFormDisplayWithNewStep( List<FormDisplay> formDisplayList, Map<Integer, Integer> mapIdSteps )
    {
        for ( FormDisplay formDisplay : formDisplayList )
        {
            formDisplay.setStepId( mapIdSteps.get( formDisplay.getStepId( ) ) );
        }
    }
}
