/*************************************************************************************************************
 * File Name   : CARTBatchManager
 * Description : This class is the base class , which handles the request to execute
				 a batch job from Tivoli WS. Based on the parameter passed from
				 tivoli, it creates a BatchService object using the
				 BatchServiceFactory class Further it calls the respective
				 BatchService doService method to implement the batch job task. It
				 tracks the status of the batch job into database, through the
				 startjob and endjob methods.
 * Roles       :
 * Known Bugs  :
 * Date Created: Aug 01, 2017
 * Created by  : L&T Infotech
 *
 **************************************************************************************************************/

package com.honda.cart2.batch;

import java.sql.Timestamp;
import java.util.Arrays;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager; import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.honda.cart2.batch.bo.EmailNotificationBO;
import com.honda.cart2.common.dto.BatchJobDetailsDTO;
import com.honda.cart2.common.util.BatchConstantsIF;




public class CARTBatchManager
{
    private static final ApplicationContext batchCtx = new ClassPathXmlApplicationContext(BatchConstantsIF.CART_BATCH_CONTEXT_XML);
    
    protected static final Logger           logger   = LogManager.getLogger(CARTBatchManager.class);
    
    public static CARTBatchServiceFactory   batchServiceFactory;

    private static CARTBatchService         batchService;
    
    private static EmailNotificationBO emailNotificationBO;

    /**
     * This is an Enum variable used to resemble the BatchJob three
     * different status.
     */
    public enum JobStatus
    {
        // 1-Running, 2-Completed, 3-Failed
         /** The INPROCESS. */
         INPROCESS(1), 
		 /** The SUCCESS. */
		 SUCCESS(2), 
		 /** The FAILED. */
		 FAILED(3);
        
        /** The value. */
        private int value;

        /**
         * Instantiates a new job status.
         *
         * @param v the v
         */
        JobStatus(int v)
        {
            value = v;
        }

        /**
         * Value.
         *
         * @return the int
         */
        public int value()
        {
            return value;
        }
    }

    /**
     * ************************************************************************************************************************
     * Method Name           : process
     * Description           :   This method is called from the main method, in order to process
     * the functionality of the respective batchJob by calling the
     * BatchService.doService method.
     * **************************************************************************************************************************
     *
     * @param batchService the batch service
     * @param batchJobId the batch job id
     * @return the int
     * @throws Exception 
     */

    public static int process(CARTBatchService batchService, int batchJobId, String batchName, String batchParam2)
            throws Exception
    {
        //if(logger.isDebugEnabled())
            logger.info("Inside CARTBatchManager process");
            System.out.println("JAVA Current JVM version (System.getProperty): " + System.getProperty("java.version"));
           System.out.println(" In GIT-new jfrog Test1");
        //boolean found = false;
        String exceptionOccured = null;
        int jobStatus = JobStatus.INPROCESS.value();
        //Start the batch job. Set status as in progress
        BatchJobDetailsDTO batchJobDetailsDTO = startJob(batchService, batchJobId, jobStatus,batchName);
        try
        {
           // if(logger.isDebugEnabled())
                logger.info("Starting of batch job task.");
                logger.info(" In GIT Test");
            //Call do service method of job being executed
            if(batchParam2==null)
            	batchService.doService();
            else
            	batchService.doService(batchParam2, batchJobDetailsDTO.getM_strBatchJobDetailsId());
            jobStatus = JobStatus.SUCCESS.value();
            if(logger.isDebugEnabled())
                logger.info("End of batch job task.");
        }
        catch (CARTBatchException ex)
        {
        	jobStatus = JobStatus.FAILED.value();
            logger.error("Exception inside CARTBatchManager process method", ex);
            exceptionOccured = ex.getExceptionStackTrace();
        }
        catch (Exception ex)
        {
            jobStatus = JobStatus.FAILED.value();
            logger.error("Exception in CARTBatchManager process", ex);
            exceptionOccured = ExceptionUtils.getStackTrace(ex);
        }
        
      //If failed send mail about failure.
		if(jobStatus==JobStatus.FAILED.value()){
			//Trigger Email for failure. cartProjectTeamEmail
			String mailBody = "<br>CART Batch Failed: "+batchJobDetailsDTO.getM_strBatchJobName()+"<br>Exception Occured: <br>"+exceptionOccured;
			String mailSubject = "";//empty as subject line is set further.
			emailNotificationBO = new EmailNotificationBO();
			emailNotificationBO.sendEmailNotification(BatchConstantsIF.EMAIL_SEND_REASON.Exception.value(), mailBody, mailSubject, batchJobDetailsDTO.getM_strBatchJobName(), null, null);
		}
        
        if(logger.isDebugEnabled())
            logger.info("Exiting method - process");
        return endJob(batchService, batchJobDetailsDTO, jobStatus);
    }

    /**
     * @param batchService the batch service
     * @param jobId the job id
     * @param jobStatus the job status
     * @return the BatchJobDetailsDTO
     * @throws Exception 
     */

    public static BatchJobDetailsDTO startJob(CARTBatchService batchService, int jobId,
            int jobStatus,String batchName) throws Exception
    {
        if(logger.isDebugEnabled())
            logger.info("Inside CARTBatchManager startJob");
        //Set values in  batch job master table object
        BatchJobDetailsDTO batchJobDetailsDTO = new BatchJobDetailsDTO();
        batchJobDetailsDTO.setM_strBatchJobId(new Integer(jobId));
        batchJobDetailsDTO.setM_strBatchJobStatus(Integer.toString(jobStatus));
        batchJobDetailsDTO.setM_strBatchJobCreatedBy("CART_BATCH");
		batchJobDetailsDTO.setM_strBatchJobModifiedBy("CART_BATCH");
		batchJobDetailsDTO.setM_strBatchJobName(batchName);
        batchService.saveJob(batchJobDetailsDTO);
		return batchJobDetailsDTO;

    }

    /**
     * ************************************************************************************************************************
     * Method Name           : endJob
     * Description           : This method is called to update the status of batch completion
     * Input param           :  jobid,jobStatus
     * **************************************************************************************************************************.
     *
     * @param batchService the batch service
     * @param batchJob the batch job
     * @param jobStatus the job status
     * @return the int
     */

    public static int endJob(CARTBatchService batchService, BatchJobDetailsDTO batchJobDetailsDTO,
            int jobStatus) throws CARTBatchException
    {
        if(logger.isDebugEnabled())
            logger.info("Inside CARTBatchManager endJob");

        if (batchJobDetailsDTO != null)
        {
        	batchJobDetailsDTO.setM_strBatchJobEndTime(new Timestamp(System.currentTimeMillis()));
            //set job status
        	batchJobDetailsDTO.setM_strBatchJobStatus(Integer.toString(jobStatus));

            //update batch job details table with status
            batchService.updateJob(batchJobDetailsDTO, false);
        }
        if(logger.isDebugEnabled())
            logger.info("Exiting CARTBatchManager endJob");
        return jobStatus;
    }

    /**
     * ************************************************************************************************************************
     * Method Name           : endJob
     * Description           :   This is the starting point of all the batch job execution. This
     * method gets the batchService bean from the spring container. It
     * then decides which batchService object needs to be created
     * based on the parameter from the Cron Shell Script. The
     * batchService object is generated through the
     * CARTBatchServiceFactory class.
     * Input param           :  String[] - parameter passed from the Cron Shell script.
     * **************************************************************************************************************************
     *
     * @param args the arguments
     */
    public static void main(String[] args)
    {
    	int status=-1;
        try
        {
            if (logger.isDebugEnabled())
            {
                logger.info("Inside CARTBatchManager main");
                logger.info("args - "+Arrays.toString(args));
            }
            logger.info("Getting batch context");
            CARTBatchService batchService = null;
            CARTBatchServiceFactory cartBatchSrvcFactory = (CARTBatchServiceFactory) batchCtx.getBean(BatchConstantsIF.CART_BATCH_BATCHSERVICE_FACTORY);
            String batchName = null;
            String batchParam2 = null;

            if ((args.length >= 1) && (args[0].trim().length() > 0))
            {
                batchName = args[0];
                if (logger.isDebugEnabled())
                {
                    logger.info(args[0]);
                }
            }
            
            if ((args.length >= 2) && (args[1].trim().length() > 0))
            {
            	batchParam2 = args[1];
                if (logger.isDebugEnabled())
                {
                    logger.info(args[1]);
                }
            }

            if (batchName != null)
            {
                batchService = cartBatchSrvcFactory.getBatchService(args[0]);

                int batchJobId = batchService.getBatchJobIdByName(args[0]);

                if (logger.isDebugEnabled())
                {
                    logger.info("BATCH JOB ID is  " + batchJobId);
                }

                /*if (fileInitial != null)
                {
                    batchService.setFileInitial(fileInitial);
                }
                if (plantCode != null)
                {
                    batchService.setPlantCode(plantCode);
                }
                if (emailFrequency != null)
                {
                    batchService.setEmailFrequency(emailFrequency);
                }*/
                if (logger.isDebugEnabled())
                    logger.info("Starting batch execution - " + batchName);
                status = process(batchService, batchJobId,batchName,batchParam2);

                if (status == Integer.parseInt(BatchConstantsIF.BATCH_STATUS.FAILED.value()))
                    logger.error("Batch Job failed during execution.");
                else
                    logger.info("Batch Job Execution Successful");
            }
        }
        catch (CARTBatchException ex)
        {
            logger.error("Exception inside CARTBatchManager main method", ex);
            System.exit(0);
        }
        catch (Exception ex)
        {
            logger.error("Exception inside CARTBatchManager main method", ex);
            System.exit(0);
        }
        if (logger.isDebugEnabled())
            logger.info("Exiting CARTBatchManager main method");
    }
}
